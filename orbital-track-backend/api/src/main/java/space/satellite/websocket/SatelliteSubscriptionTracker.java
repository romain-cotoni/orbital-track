package space.satellite.websocket;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/**
 * Tracks active per-satellite STOMP subscriptions.
 *
 * <p>Listens for STOMP subscribe/unsubscribe/disconnect events and maintains
 * the set of NORAD IDs that currently have at least one active subscriber on
 * {@code /topic/positions/{noradId}}.
 *
 * <p>Destinations that do not match the per-satellite pattern (e.g. regime
 * topics like {@code /topic/positions/leo}) are silently ignored.
 */
@Component
@Slf4j
public class SatelliteSubscriptionTracker {

    private static final String PER_SAT_PREFIX = "/topic/positions/";

    /** sessionId → set of subscriptionIds that are per-satellite */
    private final Map<String, Map<String, Integer>> sessionSubscriptions = new ConcurrentHashMap<>();

    /** noradId → number of active subscribers */
    private final Map<Integer, Integer> subscriberCount = new ConcurrentHashMap<>();

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        Integer noradId = extractNoradId(destination);
        if (noradId == null) return;

        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();

        sessionSubscriptions
            .computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
            .put(subscriptionId, noradId);

        subscriberCount.merge(noradId, 1, Integer::sum);
        log.debug("Subscribed session={} sub={} norad={} (total subscribers: {})",
            sessionId, subscriptionId, noradId, subscriberCount.get(noradId));
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();
        removeSubscription(sessionId, subscriptionId);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        Map<String, Integer> subs = sessionSubscriptions.remove(sessionId);
        if (subs == null) return;
        subs.values().forEach(noradId -> decrementCount(noradId));
        log.debug("Session {} disconnected, removed {} per-satellite subscription(s)", sessionId, subs.size());
    }

    /** Returns a snapshot of NORAD IDs that currently have at least one active subscriber. */
    public Set<Integer> getTrackedNoradIds() {
        return subscriberCount.keySet();
    }

    // -------------------------------------------------------------------------

    private void removeSubscription(String sessionId, String subscriptionId) {
        Map<String, Integer> subs = sessionSubscriptions.get(sessionId);
        if (subs == null) return;
        Integer noradId = subs.remove(subscriptionId);
        if (noradId == null) return;
        decrementCount(noradId);
        log.debug("Unsubscribed session={} sub={} norad={}", sessionId, subscriptionId, noradId);
    }

    private void decrementCount(int noradId) {
        subscriberCount.compute(noradId, (k, v) -> (v == null || v <= 1) ? null : v - 1);
    }

    private Integer extractNoradId(String destination) {
        if (destination == null || !destination.startsWith(PER_SAT_PREFIX)) return null;
        String suffix = destination.substring(PER_SAT_PREFIX.length());
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException e) {
            return null; // regime topic like /topic/positions/leo — ignore
        }
    }
}

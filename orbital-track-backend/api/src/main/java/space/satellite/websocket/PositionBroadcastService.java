package space.satellite.websocket;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import space.satellite.dtos.SatellitePosition;
import space.satellite.services.SatellitePositionService;

/**
 * Broadcasts satellite positions as binary frames over STOMP/WebSocket.
 *
 * <p>Each orbit regime has its own topic and broadcast interval:
 * <ul>
 *   <li>LEO — {@code /topic/positions/leo} — every 10 s</li>
 *   <li>MEO — {@code /topic/positions/meo} — every 30 s</li>
 *   <li>GEO — {@code /topic/positions/geo} — every 60 s</li>
 *   <li>Per-satellite — {@code /topic/positions/{noradId}} — every 5 s (only while subscribed)</li>
 * </ul>
 *
 * <p>The binary frame is encoded once and sent as a single {@code byte[]} to all
 * subscribers — no per-subscriber re-serialization.
 *
 * <p>Per-satellite topics are demand-driven: {@link SatelliteSubscriptionTracker} detects
 * active STOMP subscriptions and this service broadcasts only to those NORAD IDs.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PositionBroadcastService {

    private static final String TOPIC_LEO    = "/topic/positions/leo";
    private static final String TOPIC_MEO    = "/topic/positions/meo";
    private static final String TOPIC_GEO    = "/topic/positions/geo";
    private static final String TOPIC_HEO    = "/topic/positions/heo";
    private static final String TOPIC_SAT    = "/topic/positions/";

    // Maps a regime topic to its orbit regime string
    private static final Map<String, String> TOPIC_TO_REGIME = Map.of(
        TOPIC_LEO, "LEO",
        TOPIC_MEO, "MEO",
        TOPIC_GEO, "GEO",
        TOPIC_HEO, "HEO"
    );

    private final SatellitePositionService positionService;
    private final SimpMessagingTemplate messagingTemplate;
    private final SatelliteSubscriptionTracker subscriptionTracker;

    @Scheduled(fixedRate = 10_000)
    public void broadcastLeo() {
        broadcast("LEO", TOPIC_LEO);
    }

    @Scheduled(fixedRate = 30_000)
    public void broadcastMeo() {
        broadcast("MEO", TOPIC_MEO);
    }

    @Scheduled(fixedRate = 60_000)
    public void broadcastGeo() {
        broadcast("GEO", TOPIC_GEO);
    }

    @Scheduled(fixedRate = 30_000)
    public void broadcastHeo() {
        broadcast("HEO", TOPIC_HEO);
    }

    @Scheduled(fixedRate = 5_000)
    public void broadcastTracked() {
        Set<Integer> tracked = subscriptionTracker.getTrackedNoradIds();
        if (tracked.isEmpty()) return;

        Instant now = Instant.now();
        for (int noradId : tracked) {
            positionService.propagate(noradId, now).ifPresent(position -> {
                byte[] frame = PositionBinaryEncoder.encode(List.of(position), now);
                messagingTemplate.convertAndSend(TOPIC_SAT + noradId, frame);
                log.debug("Broadcast per-satellite norad={} ({} bytes)", noradId, frame.length);
            });
        }
    }

    /**
     * Immediately pushes current positions to a client that just subscribed to a regime topic.
     * Runs async so it doesn't block the STOMP subscription event thread.
     */
    @Async
    @EventListener
    public void onRegimeSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        String regime = TOPIC_TO_REGIME.get(destination);
        if (regime == null) return; // not a regime topic — ignore

        log.debug("New subscriber on {} — pushing immediate frame", destination);
        broadcast(regime, destination);
    }

    private void broadcast(String regime, String topic) {
        try {
            Instant now = Instant.now();
            List<SatellitePosition> positions = positionService.propagateByRegime(regime, now);
            if (positions.isEmpty()) {
                log.debug("No {} satellites to broadcast", regime);
                return;
            }
            byte[] frame = PositionBinaryEncoder.encode(positions, now);
            messagingTemplate.convertAndSend(topic, frame);
            log.debug("Broadcast {} satellites on {} ({} bytes)", positions.size(), topic, frame.length);
        } catch (Exception e) {
            log.error("Broadcast failed for {}: {}", regime, e.getMessage(), e);
        }
    }
}

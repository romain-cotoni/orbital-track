package space.satellite.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.CookieManager;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Configuration for HTTP clients used to fetch TLE data from external sources.
 * <p>
 * Configures RestClient instances with appropriate timeouts and cookie handling
 * for Space-Track (session-based auth) and CelesTrak (public API).
 * </p>
 */
@Configuration
public class RestClientConfig {

    /**
     * Creates a RestClient for Space-Track API.
     * <p>
     * Configured with:
     * <ul>
     *   <li>Cookie manager for session-based authentication</li>
     *   <li>30-second connection timeout</li>
     *   <li>5-minute read timeout (bulk fetches can be slow)</li>
     * </ul>
     * </p>
     *
     * @return configured RestClient for Space-Track
     */
    @Bean
    public RestClient spaceTrackRestClient(SpaceTrackProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMinutes(5));

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Creates a RestClient for CelesTrak API.
     * <p>
     * Used as failover when Space-Track is unavailable. No cookie handling
     * needed as CelesTrak provides public access without authentication.
     * </p>
     *
     * @return configured RestClient for CelesTrak
     */
    @Bean
    public RestClient celestrakRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMinutes(5));

        return RestClient.builder()
                .baseUrl("https://celestrak.org")
                .requestFactory(requestFactory)
                .build();
    }
}

package space.satellite.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Space-Track API authentication.
 * <p>
 * Credentials should be provided via environment variables:
 * <ul>
 *   <li>{@code SPACETRACK_IDENTITY} - Space-Track account email</li>
 *   <li>{@code SPACETRACK_PASSWORD} - Space-Track account password</li>
 * </ul>
 * </p>
 *
 * @see <a href="https://www.space-track.org">Space-Track.org</a>
 */
@Configuration
@ConfigurationProperties(prefix = "spacetrack")
@Getter
@Setter
public class SpaceTrackProperties {

    /** Space-Track account email address. */
    private String identity;

    /** Space-Track account password. */
    private String password;

    /** Space-Track base url (prod or test)*/
    private String baseUrl;
}

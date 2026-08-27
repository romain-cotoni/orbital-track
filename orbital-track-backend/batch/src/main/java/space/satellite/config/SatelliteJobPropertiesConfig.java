package space.satellite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines the 4 per-regime {@link SatelliteJobProperties} beans in a dedicated class,
 * separate from {@link BatchConfig}, to avoid a circular dependency between
 * BatchConfig and SatelliteJobCompletionListener.
 */
@Configuration
public class SatelliteJobPropertiesConfig {

    @Bean
    @ConfigurationProperties(prefix = "sattrack.batch.leo")
    public SatelliteJobProperties leoJobProperties() {
        return new SatelliteJobProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "sattrack.batch.meo")
    public SatelliteJobProperties meoJobProperties() {
        return new SatelliteJobProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "sattrack.batch.heo")
    public SatelliteJobProperties heoJobProperties() {
        return new SatelliteJobProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "sattrack.batch.geo")
    public SatelliteJobProperties geoJobProperties() {
        return new SatelliteJobProperties();
    }
}

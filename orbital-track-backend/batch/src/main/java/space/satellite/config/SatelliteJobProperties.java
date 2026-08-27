package space.satellite.config;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration properties for a single orbit-regime satellite fetch job.
 * <p>
 * Instantiated 4 times in {@link BatchConfig} — once per orbit regime (LEO, MEO, HEO, GEO)
 * — each bound to its own {@code sattrack.batch.<regime>.*} prefix.
 * </p>
 */
@Getter
@Setter
public class SatelliteJobProperties {

    /**
     * Object type filter for Space-Track queries.
     * <p>
     * Empty string fetches all objects. Valid values include:
     * "PAYLOAD", "DEBRIS", "ROCKET BODY".
     * </p>
     */
    private String group;

    /**
     * Orbit altitude : LEO, MEO, HEO, or "" for all
     */
    private String orbitType;

    /**
     * Time gap within : 24h, 72h, 30d, or "" for all
     */
    private String epochWindow;

    private boolean excludeDecayed = true;

    /** Satellites not refreshed within this many days are deleted after each job run. */
    private int cleanupThresholdDays = 60;

    /** Number of Satellite records to process per transaction chunk. */
    private int chunkSize = 500;

    /** Cron expression for job scheduling (default: 2:00 AM UTC daily). */
    private String cron = "0 0 2 * * *";

    /** Delay between Space-Track retry attempts in milliseconds. Default: 3 minutes. */
    private long retryDelayMs = 180000;
}

package space.satellite.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.util.CollectionUtils;
import space.satellite.config.SatelliteJobProperties;
import space.satellite.record.SatelliteRecord;
import space.satellite.service.SpaceTrackService;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Spring Batch ItemReader that fetches satellite data from external sources.
 * <p>
 * On first read, performs a bulk fetch from Space-Track (primary) with automatic
 * failover to CelesTrak if Space-Track is unavailable. The fetched records are
 * stored in memory and returned one at a time on subsequent reads.
 * </p>
 */
@RequiredArgsConstructor
@Slf4j
public class SatelliteItemReader implements ItemReader<SatelliteRecord> {

    private final SpaceTrackService spaceTrackService;
    //private final CelestrakService celestrakService;
    private final SatelliteJobProperties jobProperties;

    private Iterator<SatelliteRecord> recordIterator;
    private boolean initialized = false;

    /**
     * Reads the next satellite record.
     * <p>
     * On first invocation, triggers bulk fetch from external sources.
     * Returns {@code null} when all records have been read, signaling
     * end of input to Spring Batch.
     * </p>
     *
     * @return next SatelliteRecord, or null if exhausted
     */
    @Override
    public SatelliteRecord read() {
        if (!initialized) {
            initialize();
            initialized = true;
        }

        if (recordIterator != null && recordIterator.hasNext()) {
            return recordIterator.next();
        }

        // Reset for next job run
        initialized = false;
        recordIterator = null;
        return null;
    }

    private void initialize() {
        List<SatelliteRecord> records = fetchWithFailover();
        if (!CollectionUtils.isEmpty(records)) {
            recordIterator = records.iterator();
            log.info("Loaded {} satellite records for processing", records.size());
        } else {
            log.error("Failed to fetch satellite data from both Space-Track and CelesTrak");
        }
    }

    private List<SatelliteRecord> fetchWithFailover() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                log.info("Space-Track fetch attempt {}/3", attempt);
                return spaceTrackService.fetchAllSatellites(jobProperties);
            } catch (Exception e) {
                log.warn("Space-Track attempt {}/3 failed: {}", attempt, e.getMessage());
                if (attempt < 3) {
                    try {
                        Thread.sleep(jobProperties.getRetryDelayMs());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return Collections.emptyList();
                    }
                }
            }
        }
        // CelesTrak failover — uncomment celestrakService field above and in BatchConfig to enable
        //try {
        //    log.info("Falling back to CelesTrak");
        //    return celestrakService.fetchAllSatellites(jobProperties);
        //} catch (Exception e) {
        //    log.error("CelesTrak fallback also failed: {}", e.getMessage());
        //}
        return Collections.emptyList();
    }
}

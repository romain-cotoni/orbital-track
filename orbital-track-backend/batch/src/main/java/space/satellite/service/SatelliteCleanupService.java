package space.satellite.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.satellite.repositories.SatelliteRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Deletes satellite records that have not been refreshed by any recent batch run.
 * Called from {@link space.satellite.listener.SatelliteJobCompletionListener} after
 * each successful job execution.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SatelliteCleanupService {

    private final SatelliteRepository satelliteRepository;

    @Transactional
    public void cleanupStale(String orbitRegime, int thresholdDays) {
        Instant cutoff = Instant.now().minus(thresholdDays, ChronoUnit.DAYS);
        log.info("Cleaning up stale {} satellites not seen since {}", orbitRegime, cutoff);
        int deleted = satelliteRepository.deleteStaleByOrbitRegime(orbitRegime, cutoff);
        log.info("Deleted {} stale {} satellite(s)", deleted, orbitRegime);
    }
}

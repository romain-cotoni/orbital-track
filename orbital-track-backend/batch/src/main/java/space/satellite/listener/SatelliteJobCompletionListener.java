package space.satellite.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import space.satellite.config.SatelliteJobProperties;
import space.satellite.service.SatelliteCleanupService;
import space.satellite.services.PropagatorCacheService;

import java.time.Duration;
import java.util.Map;

/**
 * Spring Batch job listener for the Satellite fetch job.
 * <p>
 * Handles post-job actions:
 * <ul>
 *   <li>Logs job statistics (duration, records processed)</li>
 *   <li>Triggers propagator cache rebuild on successful completion</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SatelliteJobCompletionListener implements JobExecutionListener {

    private final PropagatorCacheService propagatorCacheService;
    private final SatelliteCleanupService satelliteCleanupService;

    /** All SatelliteJobProperties beans, keyed by bean name (e.g. "leoJobProperties"). */
    @Autowired
    private Map<String, SatelliteJobProperties> allJobProperties;

    /**
     * Called before job execution starts.
     *
     * @param jobExecution the job execution context
     */
    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("{} satellite fetch job started with execution ID: {}",
                jobExecution.getJobInstance().getJobName(), jobExecution.getId());
    }

    /**
     * Called after job execution completes.
     * <p>
     * Logs job statistics and triggers cache rebuild if the job completed
     * successfully. Cache rebuild errors are logged but do not fail the job.
     * </p>
     *
     * @param jobExecution the job execution context
     */
    @Override
    public void afterJob(JobExecution jobExecution) {
        Duration duration = Duration.between(
                jobExecution.getStartTime(),
                jobExecution.getEndTime()
        );

        long readCount = jobExecution.getStepExecutions().stream()
                .mapToLong(step -> step.getReadCount())
                .sum();

        long writeCount = jobExecution.getStepExecutions().stream()
                .mapToLong(step -> step.getWriteCount())
                .sum();

        log.info("Satellite fetch job completed with status: {}", jobExecution.getStatus());
        log.info("Job statistics - Duration: {}s, Records read: {}, Records written: {}",
                duration.toSeconds(), readCount, writeCount);

        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            log.info("Triggering propagator cache rebuild...");
            try {
                propagatorCacheService.rebuildCache();
                log.info("Propagator cache rebuild completed successfully");
            } catch (Exception e) {
                log.error("Failed to rebuild propagator cache", e);
            }

            try {
                String jobName = jobExecution.getJobInstance().getJobName();
                // e.g. "leoSatelliteFetchJob" → regime "LEO", props key "leoJobProperties"
                String prefix = jobName.replace("SatelliteFetchJob", "");
                String orbitRegime = prefix.isBlank() ? "GEO" : prefix.toUpperCase();
                SatelliteJobProperties props = allJobProperties.get(prefix + "JobProperties");
                if (props != null) {
                    satelliteCleanupService.cleanupStale(orbitRegime, props.getCleanupThresholdDays());
                }
            } catch (Exception e) {
                log.error("Failed to clean up stale satellites", e);
            }

            log.info("{} satellite fetch job ended successfully with execution ID: {}",
                    jobExecution.getJobInstance().getJobName(), jobExecution.getId());
        } else {
            log.warn("Skipping cache rebuild due to job status: {}", jobExecution.getStatus());
            log.warn("{} satellite fetch job ended with status {} and execution ID: {}",
                    jobExecution.getJobInstance().getJobName(), jobExecution.getStatus(), jobExecution.getId());
        }
    }
}

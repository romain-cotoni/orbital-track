package space.satellite.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Scheduler that triggers the 4 orbit-regime satellite fetch jobs on independent schedules.
 * <p>
 * Each job fires according to its own cron expression, tuned to how quickly TLEs age
 * for that orbit regime:
 * <ul>
 *   <li>LEO — every 2 hours (high atmospheric drag)</li>
 *   <li>MEO — daily (stable GPS/Galileo orbits)</li>
 *   <li>HEO — every 12 hours (elliptical, variable dynamics)</li>
 *   <li>GEO — weekly (geostationary, very stable)</li>
 * </ul>
 * </p>
 */
@Component
@Slf4j
public class SatelliteJobScheduler {

    private final JobOperator jobOperator;
    private final Job leoSatelliteFetchJob;
    private final Job meoSatelliteFetchJob;
    private final Job heoSatelliteFetchJob;
    private final Job geoSatelliteFetchJob;

    public SatelliteJobScheduler(
            JobOperator jobOperator,
            @Qualifier("leoSatelliteFetchJob") Job leoSatelliteFetchJob,
            @Qualifier("meoSatelliteFetchJob") Job meoSatelliteFetchJob,
            @Qualifier("heoSatelliteFetchJob") Job heoSatelliteFetchJob,
            @Qualifier("geoSatelliteFetchJob") Job geoSatelliteFetchJob
    ) {
        this.jobOperator = jobOperator;
        this.leoSatelliteFetchJob = leoSatelliteFetchJob;
        this.meoSatelliteFetchJob = meoSatelliteFetchJob;
        this.heoSatelliteFetchJob = heoSatelliteFetchJob;
        this.geoSatelliteFetchJob = geoSatelliteFetchJob;
    }

    @Scheduled(cron = "${sattrack.batch.leo.cron}")
    public void runLeoSatelliteFetchJob() {
        runJob(leoSatelliteFetchJob, "LEO");
    }

    @Scheduled(cron = "${sattrack.batch.meo.cron}")
    public void runMeoSatelliteFetchJob() {
        runJob(meoSatelliteFetchJob, "MEO");
    }

    @Scheduled(cron = "${sattrack.batch.heo.cron}")
    public void runHeoSatelliteFetchJob() {
        runJob(heoSatelliteFetchJob, "HEO");
    }

    @Scheduled(cron = "${sattrack.batch.geo.cron}")
    public void runGeoSatelliteFetchJob() {
        runJob(geoSatelliteFetchJob, "GEO");
    }

    private void runJob(Job job, String orbitType) {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("run.id", Instant.now().toString())
                    .toJobParameters();
            jobOperator.start(job, jobParameters);
        } catch (Exception e) {
            log.error("Failed to launch {} satellite fetch job", orbitType, e);
        }
    }
}

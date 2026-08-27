package space.satellite.config;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import space.satellite.batch.SatelliteItemProcessor;
import space.satellite.batch.SatelliteItemReader;
import space.satellite.batch.SatelliteItemWriter;
import space.satellite.entities.Satellite;
import space.satellite.listener.SatelliteJobCompletionListener;
import space.satellite.record.SatelliteRecord;
import space.satellite.service.CelestrakService;
import space.satellite.service.SpaceTrackService;

/**
 * Spring Batch configuration defining 4 independent satellite fetch jobs, one per orbit regime.
 * <p>
 * Each job has its own {@link SatelliteJobProperties} bean (bound to its own config prefix),
 * its own {@link SatelliteItemReader} instance (stateful, not shared), and its own
 * {@link Step}. {@link SatelliteItemProcessor} and {@link SatelliteItemWriter} are shared
 * singletons across all 4 jobs.
 * </p>
 */
@Configuration
@RequiredArgsConstructor
public class BatchConfig {

    private final SatelliteJobCompletionListener satelliteJobCompletionListener;
    private final SpaceTrackService spaceTrackService;
    //private final CelestrakService celestrakService;

    // -------------------------------------------------------------------------
    // Reader beans — one per orbit regime (stateful, must not be shared)
    // -------------------------------------------------------------------------

    @Bean
    public SatelliteItemReader leoSatelliteItemReader(SatelliteJobProperties leoJobProperties) {
        return new SatelliteItemReader(spaceTrackService/*, celestrakService*/, leoJobProperties);
    }

    @Bean
    public SatelliteItemReader meoSatelliteItemReader(SatelliteJobProperties meoJobProperties) {
        return new SatelliteItemReader(spaceTrackService/*, celestrakService*/, meoJobProperties);
    }

    @Bean
    public SatelliteItemReader heoSatelliteItemReader(SatelliteJobProperties heoJobProperties) {
        return new SatelliteItemReader(spaceTrackService/*, celestrakService*/, heoJobProperties);
    }

    @Bean
    public SatelliteItemReader geoSatelliteItemReader(SatelliteJobProperties geoJobProperties) {
        return new SatelliteItemReader(spaceTrackService/*, celestrakService*/, geoJobProperties);
    }

    // -------------------------------------------------------------------------
    // Steps — one per orbit regime
    // -------------------------------------------------------------------------

    @Bean
    public Step leoFetchStep(
            JobRepository jobRepository,
            @Qualifier("transactionManager") PlatformTransactionManager transactionManager,
            SatelliteItemReader leoSatelliteItemReader,
            SatelliteItemProcessor processor,
            SatelliteItemWriter writer,
            SatelliteJobProperties leoJobProperties
    ) {
        return new StepBuilder("leoFetchStep", jobRepository)
                .<SatelliteRecord, Satellite>chunk(leoJobProperties.getChunkSize())
                .transactionManager(transactionManager)
                .reader(leoSatelliteItemReader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    public Step meoFetchStep(
            JobRepository jobRepository,
            @Qualifier("transactionManager") PlatformTransactionManager transactionManager,
            SatelliteItemReader meoSatelliteItemReader,
            SatelliteItemProcessor processor,
            SatelliteItemWriter writer,
            SatelliteJobProperties meoJobProperties
    ) {
        return new StepBuilder("meoFetchStep", jobRepository)
                .<SatelliteRecord, Satellite>chunk(meoJobProperties.getChunkSize())
                .transactionManager(transactionManager)
                .reader(meoSatelliteItemReader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    public Step heoFetchStep(
            JobRepository jobRepository,
            @Qualifier("transactionManager") PlatformTransactionManager transactionManager,
            SatelliteItemReader heoSatelliteItemReader,
            SatelliteItemProcessor processor,
            SatelliteItemWriter writer,
            SatelliteJobProperties heoJobProperties
    ) {
        return new StepBuilder("heoFetchStep", jobRepository)
                .<SatelliteRecord, Satellite>chunk(heoJobProperties.getChunkSize())
                .transactionManager(transactionManager)
                .reader(heoSatelliteItemReader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    public Step geoFetchStep(
            JobRepository jobRepository,
            @Qualifier("transactionManager") PlatformTransactionManager transactionManager,
            SatelliteItemReader geoSatelliteItemReader,
            SatelliteItemProcessor processor,
            SatelliteItemWriter writer,
            SatelliteJobProperties geoJobProperties
    ) {
        return new StepBuilder("geoFetchStep", jobRepository)
                .<SatelliteRecord, Satellite>chunk(geoJobProperties.getChunkSize())
                .transactionManager(transactionManager)
                .reader(geoSatelliteItemReader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    // -------------------------------------------------------------------------
    // Jobs — one per orbit regime
    // -------------------------------------------------------------------------

    @Bean
    public Job leoSatelliteFetchJob(JobRepository jobRepository, Step leoFetchStep) {
        return new JobBuilder("leoSatelliteFetchJob", jobRepository)
                .start(leoFetchStep)
                .listener(satelliteJobCompletionListener)
                .build();
    }

    @Bean
    public Job meoSatelliteFetchJob(JobRepository jobRepository, Step meoFetchStep) {
        return new JobBuilder("meoSatelliteFetchJob", jobRepository)
                .start(meoFetchStep)
                .listener(satelliteJobCompletionListener)
                .build();
    }

    @Bean
    public Job heoSatelliteFetchJob(JobRepository jobRepository, Step heoFetchStep) {
        return new JobBuilder("heoSatelliteFetchJob", jobRepository)
                .start(heoFetchStep)
                .listener(satelliteJobCompletionListener)
                .build();
    }

    @Bean
    public Job geoSatelliteFetchJob(JobRepository jobRepository, Step geoFetchStep) {
        return new JobBuilder("geoSatelliteFetchJob", jobRepository)
                .start(geoFetchStep)
                .listener(satelliteJobCompletionListener)
                .build();
    }
}

package space.satellite.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;
import space.satellite.entities.Satellite;
import space.satellite.repositories.SatelliteRepository;

/**
 * Spring Batch ItemWriter that persists Satellite entities to the database.
 * <p>
 * Uses upsert logic to insert new satellites or update existing ones based on
 * NORAD Catalog ID. This ensures the database always contains the latest
 * data for each tracked object.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SatelliteItemWriter implements ItemWriter<Satellite> {

    private final SatelliteRepository satelliteRepository;

    @Override
    public void write(Chunk<? extends Satellite> chunk) {
        log.debug("Writing {} satellite records to database", chunk.size());

        for (Satellite s : chunk) {
            satelliteRepository.upsert(
                    s.getNoradCatId(),
                    s.getName(),
                    s.getObjectType(),
                    s.getCountryCode(),
                    s.getLaunchDate(),
                    s.getDecayDate(),
                    s.getRcsSize(),
                    s.getOrbitRegime(),
                    s.getEpoch(),
                    s.getLine1(),
                    s.getLine2(),
                    s.getFetchedAt(),
                    s.getSource(),
                    s.getConstellation(),
                    s.getMissionType()
            );
        }

        log.debug("Successfully wrote {} satellite records", chunk.size());
    }
}

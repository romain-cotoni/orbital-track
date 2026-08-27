package space.satellite.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import space.satellite.entities.Satellite;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Satellite} entity persistence operations.
 * <p>
 * Provides standard CRUD + specification-based filtering via JPA,
 * plus a native upsert for efficient bulk batch updates.
 * </p>
 */
@Repository
public interface SatelliteRepository extends JpaRepository<Satellite, Long>, JpaSpecificationExecutor<Satellite> {

    /**
     * Finds a satellite by its NORAD Catalog ID.
     *
     * @param noradCatId the NORAD catalog identifier
     * @return an Optional containing the satellite if found
     */
    Optional<Satellite> findByNoradCatId(Integer noradCatId);

    List<Satellite> findAllByOrbitRegime(String orbitRegime);

    @Query("SELECT s.objectType, COUNT(s) FROM Satellite s WHERE s.objectType IS NOT NULL GROUP BY s.objectType")
    List<Object[]> countByObjectType();

    @Query("SELECT s.orbitRegime, COUNT(s) FROM Satellite s WHERE s.orbitRegime IS NOT NULL GROUP BY s.orbitRegime")
    List<Object[]> countByOrbitRegime();

    @Query("SELECT s.countryCode, COUNT(s) FROM Satellite s WHERE s.countryCode IS NOT NULL GROUP BY s.countryCode")
    List<Object[]> countByCountryCode();

    @Modifying
    @Query("DELETE FROM Satellite s WHERE s.orbitRegime = :orbitRegime AND s.fetchedAt < :cutoff")
    int deleteStaleByOrbitRegime(@Param("orbitRegime") String orbitRegime, @Param("cutoff") Instant cutoff);

    /**
     * Inserts a new satellite or updates an existing one based on NORAD Catalog ID.
     * <p>
     * Uses PostgreSQL's ON CONFLICT clause for an atomic upsert.
     * Stable metadata fields (country_code, launch_date, rcs_size) are also refreshed
     * on each run so Space-Track corrections propagate automatically.
     * </p>
     */
    @Modifying
    @Query(value = """
            INSERT INTO satellite (
                norad_cat_id, name, object_type, country_code,
                launch_date, decay_date, rcs_size, orbit_regime,
                epoch, line1, line2, fetched_at, source,
                constellation, mission_type
            ) VALUES (
                :noradCatId, :name, :objectType, :countryCode,
                :launchDate, :decayDate, :rcsSize, :orbitRegime,
                :epoch, :line1, :line2, :fetchedAt, :source,
                :constellation, :missionType
            )
            ON CONFLICT (norad_cat_id) DO UPDATE SET
                name          = EXCLUDED.name,
                object_type   = EXCLUDED.object_type,
                country_code  = EXCLUDED.country_code,
                launch_date   = EXCLUDED.launch_date,
                decay_date    = EXCLUDED.decay_date,
                rcs_size      = EXCLUDED.rcs_size,
                orbit_regime  = EXCLUDED.orbit_regime,
                epoch         = EXCLUDED.epoch,
                line1         = EXCLUDED.line1,
                line2         = EXCLUDED.line2,
                fetched_at    = EXCLUDED.fetched_at,
                source        = EXCLUDED.source,
                constellation = EXCLUDED.constellation,
                mission_type  = EXCLUDED.mission_type
            """, nativeQuery = true)
    void upsert(
            @Param("noradCatId")    Integer   noradCatId,
            @Param("name")          String    name,
            @Param("objectType")    String    objectType,
            @Param("countryCode")   String    countryCode,
            @Param("launchDate")    LocalDate launchDate,
            @Param("decayDate")     LocalDate decayDate,
            @Param("rcsSize")       String    rcsSize,
            @Param("orbitRegime")   String    orbitRegime,
            @Param("epoch")         Instant   epoch,
            @Param("line1")         String    line1,
            @Param("line2")         String    line2,
            @Param("fetchedAt")     Instant   fetchedAt,
            @Param("source")        String    source,
            @Param("constellation") String    constellation,
            @Param("missionType")   String    missionType
    );
}

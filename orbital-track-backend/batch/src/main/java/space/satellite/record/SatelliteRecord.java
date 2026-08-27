package space.satellite.record;

import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Immutable data transfer object for satellite data during batch processing.
 * <p>
 * Carries parsed GP (General Perturbation) data from Space-Track between the
 * reader and processor stages, decoupling JSON deserialization from entity creation.
 * </p>
 *
 * @param noradCatId  NORAD catalog identifier
 * @param name        satellite common name (e.g., "ISS (ZARYA)")
 * @param objectType  PAYLOAD, ROCKET BODY, DEBRIS, TBA
 * @param countryCode two-letter country or org code
 * @param launchDate  date of launch
 * @param decayDate   date of re-entry, or null if still active
 * @param rcsSize     radar cross-section size: SMALL, MEDIUM, LARGE
 * @param orbitRegime orbit regime from batch filter: LEO, MEO, HEO
 * @param epoch       TLE epoch extracted from the GP record
 * @param line1       TLE line 1
 * @param line2       TLE line 2
 * @param source      data source identifier ("spacetrack" or "celestrak")
 */
@Builder
public record SatelliteRecord(
        Integer noradCatId,
        String name,
        String objectType,
        String countryCode,
        LocalDate launchDate,
        LocalDate decayDate,
        String rcsSize,
        String orbitRegime,
        Instant epoch,
        String line1,
        String line2,
        String source
) {}

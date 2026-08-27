package space.satellite.dtos;

import space.satellite.entities.Satellite;

import java.time.LocalDate;

/**
 * Full satellite detail response: metadata + current propagated position.
 * Used by {@code GET /api/satellites/{noradId}}.
 * {@code position} is null when propagation fails (no TLE, hyperbolic orbit, etc.).
 */
public record SatelliteDetailDto(
    Integer          noradCatId,
    String           name,
    String           objectType,
    String           countryCode,
    LocalDate        launchDate,
    LocalDate        decayDate,
    String           rcsSize,
    String           orbitRegime,
    String           source,
    SatellitePosition position
) {
    public static SatelliteDetailDto from(Satellite s, SatellitePosition position) {
        return new SatelliteDetailDto(
            s.getNoradCatId(),
            s.getName(),
            s.getObjectType(),
            s.getCountryCode(),
            s.getLaunchDate(),
            s.getDecayDate(),
            s.getRcsSize(),
            s.getOrbitRegime(),
            s.getSource(),
            position
        );
    }
}

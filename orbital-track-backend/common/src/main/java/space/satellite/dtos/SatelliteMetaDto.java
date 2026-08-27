package space.satellite.dtos;

import space.satellite.entities.Satellite;

import java.time.LocalDate;

/**
 * Satellite metadata response — no position data.
 * Used by the paginated list endpoint {@code GET /api/satellites}.
 */
public record SatelliteMetaDto(
    Integer noradCatId,
    String  name,
    String  objectType,
    String  countryCode,
    LocalDate launchDate,
    LocalDate decayDate,
    String  rcsSize,
    String  orbitRegime,
    String  source,
    String  constellation,
    String  missionType
) {
    public static SatelliteMetaDto from(Satellite s) {
        return new SatelliteMetaDto(
            s.getNoradCatId(),
            s.getName(),
            s.getObjectType(),
            s.getCountryCode(),
            s.getLaunchDate(),
            s.getDecayDate(),
            s.getRcsSize(),
            s.getOrbitRegime(),
            s.getSource(),
            s.getConstellation(),
            s.getMissionType()
        );
    }
}

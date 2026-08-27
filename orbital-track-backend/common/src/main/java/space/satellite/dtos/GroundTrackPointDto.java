package space.satellite.dtos;

import java.time.Instant;

/**
 * A single lat/lon/alt sample along a satellite's ground track.
 * Used by {@code GET /api/satellites/{noradId}/ground-track}.
 */
public record GroundTrackPointDto(
    double latDeg,
    double lonDeg,
    double altM,
    Instant time
) {
    public static GroundTrackPointDto from(SatellitePosition p) {
        return new GroundTrackPointDto(p.latDeg(), p.lonDeg(), p.altM(), p.propagatedAt());
    }
}

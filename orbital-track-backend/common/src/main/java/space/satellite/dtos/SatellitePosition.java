package space.satellite.dtos;

import java.time.Instant;

/**
 * Lean internal record representing a satellite's propagated state at a given instant.
 * Used by the WebSocket broadcast (Phase 3) and the REST API (Phase 2).
 *
 * @param noradId      NORAD Catalog ID
 * @param orbitRegime  LEO / MEO / HEO / GEO
 * @param latDeg       Geodetic latitude in degrees (WGS84)
 * @param lonDeg       Geodetic longitude in degrees (WGS84)
 * @param altM         Altitude above WGS84 ellipsoid in metres
 * @param vxMs         ITRF velocity X component (m/s)
 * @param vyMs         ITRF velocity Y component (m/s)
 * @param vzMs         ITRF velocity Z component (m/s)
 * @param propagatedAt The requested propagation instant
 */
public record SatellitePosition(
    int noradId,
    String orbitRegime,
    double latDeg,
    double lonDeg,
    double altM,
    double vxMs,
    double vyMs,
    double vzMs,
    Instant propagatedAt
) {}

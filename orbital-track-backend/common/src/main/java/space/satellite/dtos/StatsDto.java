package space.satellite.dtos;

import java.util.Map;

/**
 * Aggregate counts used by {@code GET /api/stats}.
 */
public record StatsDto(
    Map<String, Long> byObjectType,
    Map<String, Long> byOrbitRegime,
    Map<String, Long> byCountryCode
) {}

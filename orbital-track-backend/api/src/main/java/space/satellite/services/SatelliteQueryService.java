package space.satellite.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import space.satellite.dtos.GroundTrackPointDto;
import space.satellite.dtos.SatelliteDetailDto;
import space.satellite.dtos.SatelliteMetaDto;
import space.satellite.dtos.SatellitePosition;
import space.satellite.dtos.StatsDto;
import space.satellite.entities.Satellite;
import space.satellite.repositories.SatelliteRepository;
import space.satellite.repositories.SatelliteSpecification;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SatelliteQueryService {

    private final SatelliteRepository satelliteRepository;
    private final SatellitePositionService satellitePositionService;

    // -------------------------------------------------------------------------
    // GET /api/satellites — paginated + filterable list
    // -------------------------------------------------------------------------

    public Page<SatelliteMetaDto> search(Integer noradCatId, String name, String objectType,
                                         String countryCode, String orbitRegime,
                                         String constellation, String missionType,
                                         Pageable pageable) {
        Specification<Satellite> spec = Specification
            .where(SatelliteSpecification.hasNoradCatId(noradCatId))
            .and(SatelliteSpecification.hasName(name))
            .and(SatelliteSpecification.hasObjectType(objectType))
            .and(SatelliteSpecification.hasCountryCode(countryCode))
            .and(SatelliteSpecification.hasOrbitRegime(orbitRegime))
            .and(SatelliteSpecification.hasConstellation(constellation))
            .and(SatelliteSpecification.hasMissionType(missionType));

        return satelliteRepository.findAll(spec, pageable).map(SatelliteMetaDto::from);
    }

    // -------------------------------------------------------------------------
    // GET /api/satellites/{noradId} — detail with current position
    // -------------------------------------------------------------------------

    public SatelliteDetailDto getDetail(int noradId) {
        Satellite sat = satelliteRepository.findByNoradCatId(noradId)
            .orElseThrow(() -> new NoSuchElementException("Satellite not found: " + noradId));
        SatellitePosition position = satellitePositionService.propagate(noradId, Instant.now()).orElse(null);
        return SatelliteDetailDto.from(sat, position);
    }

    // -------------------------------------------------------------------------
    // GET /api/satellites/{noradId}/position?time= — position at given time
    // -------------------------------------------------------------------------

    public SatellitePosition getPositionAt(int noradId, Instant time) {
        if (!satelliteRepository.findByNoradCatId(noradId).isPresent()) {
            throw new NoSuchElementException("Satellite not found: " + noradId);
        }
        return satellitePositionService.propagate(noradId, time)
            .orElseThrow(() -> new IllegalStateException("Could not propagate position for NORAD " + noradId));
    }

    // -------------------------------------------------------------------------
    // GET /api/satellites/{noradId}/ground-track?duration= — ground track
    // -------------------------------------------------------------------------

    public List<GroundTrackPointDto> getGroundTrack(int noradId, String durationParam) {
        if (!satelliteRepository.findByNoradCatId(noradId).isPresent()) {
            throw new NoSuchElementException("Satellite not found: " + noradId);
        }
        Duration duration = parseDuration(durationParam);
        List<SatellitePosition> track = satellitePositionService.propagateGroundTrack(
            noradId, Instant.now(), duration, Duration.ofSeconds(30));
        return track.stream().map(GroundTrackPointDto::from).toList();
    }

    // -------------------------------------------------------------------------
    // GET /api/stats — aggregate counts
    // -------------------------------------------------------------------------

    public StatsDto getStats() {
        Map<String, Long> byObjectType = toMap(satelliteRepository.countByObjectType());
        Map<String, Long> byOrbitRegime = toMap(satelliteRepository.countByOrbitRegime());
        Map<String, Long> byCountryCode = toMap(satelliteRepository.countByCountryCode());
        return new StatsDto(byObjectType, byOrbitRegime, byCountryCode);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Map<String, Long> toMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(
            row -> (String) row[0],
            row -> (Long) row[1]
        ));
    }

    /**
     * Parses duration strings like "90m", "2h", "30s", "1d".
     * Throws {@link IllegalArgumentException} for unrecognized formats.
     */
    static Duration parseDuration(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("duration parameter is required (e.g. 90m, 2h)");
        }
        String v = s.trim();
        char unit = v.charAt(v.length() - 1);
        try {
            long amount = Long.parseLong(v.substring(0, v.length() - 1));
            return switch (unit) {
                case 's' -> Duration.ofSeconds(amount);
                case 'm' -> Duration.ofMinutes(amount);
                case 'h' -> Duration.ofHours(amount);
                case 'd' -> Duration.ofDays(amount);
                default  -> throw new IllegalArgumentException("Unknown duration unit '" + unit + "'. Use s, m, h, or d (e.g. 90m, 2h)");
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid duration format: '" + s + "'. Expected e.g. 90m, 2h, 30s");
        }
    }
}

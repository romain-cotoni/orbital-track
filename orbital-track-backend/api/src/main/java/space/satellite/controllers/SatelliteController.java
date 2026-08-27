package space.satellite.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import space.satellite.dtos.GroundTrackPointDto;
import space.satellite.dtos.SatelliteDetailDto;
import space.satellite.dtos.SatelliteMetaDto;
import space.satellite.dtos.SatellitePosition;
import space.satellite.dtos.StatsDto;
import space.satellite.services.SatelliteQueryService;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SatelliteController {

    private final SatelliteQueryService satelliteQueryService;

    /**
     * Paginated, filterable list of satellites (metadata only).
     * Example: {@code GET /api/satellites?orbitRegime=LEO&page=0&size=50}
     */
    @GetMapping("/satellites")
    public ResponseEntity<Page<SatelliteMetaDto>> list(
        @RequestParam(required = false) Integer noradCatId,
        @RequestParam(required = false) String name,
        @RequestParam(required = false) String objectType,
        @RequestParam(required = false) String countryCode,
        @RequestParam(required = false) String orbitRegime,
        @RequestParam(required = false) String constellation,
        @RequestParam(required = false) String missionType,
        @PageableDefault(size = 50) Pageable pageable
    ) {
        return ResponseEntity.ok(satelliteQueryService.search(noradCatId, name, objectType, countryCode, orbitRegime, constellation, missionType, pageable));
    }

    /**
     * Full satellite detail: metadata + current propagated position.
     * Example: {@code GET /api/satellites/25544}
     */
    @GetMapping("/satellites/{noradId}")
    public ResponseEntity<SatelliteDetailDto> detail(@PathVariable int noradId) {
        return ResponseEntity.ok(satelliteQueryService.getDetail(noradId));
    }

    /**
     * Satellite position at an arbitrary time.
     * Example: {@code GET /api/satellites/25544/position?time=2025-01-01T12:00:00Z}
     * Omit {@code time} to get the current position.
     */
    @GetMapping("/satellites/{noradId}/position")
    public ResponseEntity<SatellitePosition> position(
        @PathVariable int noradId,
        @RequestParam(required = false) String time
    ) {
        Instant instant = time != null ? Instant.parse(time) : Instant.now();
        return ResponseEntity.ok(satelliteQueryService.getPositionAt(noradId, instant));
    }

    /**
     * Ground track: ordered lat/lon/alt samples over a given duration (30 s interval).
     * Example: {@code GET /api/satellites/25544/ground-track?duration=90m}
     */
    @GetMapping("/satellites/{noradId}/ground-track")
    public ResponseEntity<List<GroundTrackPointDto>> groundTrack(
        @PathVariable int noradId,
        @RequestParam(defaultValue = "90m") String duration
    ) {
        return ResponseEntity.ok(satelliteQueryService.getGroundTrack(noradId, duration));
    }

    /**
     * Aggregate counts by objectType, orbitRegime, and countryCode.
     * Example: {@code GET /api/stats}
     */
    @GetMapping("/stats")
    public ResponseEntity<StatsDto> stats() {
        return ResponseEntity.ok(satelliteQueryService.getStats());
    }
}

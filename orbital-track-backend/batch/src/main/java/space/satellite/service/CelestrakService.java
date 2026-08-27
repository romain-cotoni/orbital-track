package space.satellite.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import space.satellite.config.SatelliteJobProperties;
import space.satellite.record.CelestrakGpRecord;
import space.satellite.record.SatelliteRecord;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for fetching satellite GP data from CelesTrak in JSON format.
 * Used as a failover source when Space-Track is unavailable.
 * Provides public access without authentication.
 * <p>
 * Note: CelesTrak does not include metadata fields (countryCode, launchDate,
 * rcsSize, decayDate) — those will be null in the resulting {@link SatelliteRecord}s.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CelestrakService {

    private static final String SOURCE = "celestrak";
    private static final String BASE_URL = "/NORAD/elements/gp.php";
    private static final DateTimeFormatter EPOCH_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");

    private final RestClient celestrakRestClient;

    /**
     * Fetches all GP records from CelesTrak and returns them as {@link SatelliteRecord}s.
     *
     * @param props job properties containing the group/orbit filter for the query
     */
    public List<SatelliteRecord> fetchAllSatellites(SatelliteJobProperties props) {
        String url = buildUrl(props);
        log.info("Fetching GP data from CelesTrak: {}", url);

        List<CelestrakGpRecord> gpRecords = celestrakRestClient.get()
                .uri(url)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        log.info("Received {} GP records from CelesTrak", gpRecords != null ? gpRecords.size() : 0);
        return parseJsonFormat(gpRecords != null ? gpRecords : List.of());
    }

    private String buildUrl(SatelliteJobProperties props) {
        String group = props.getGroup();
        String groupParam;

        if (group == null || group.isBlank()) {
            groupParam = "active";
        } else {
            groupParam = switch (group.toUpperCase()) {
                case "PAYLOAD"      -> "active";
                case "DEBRIS"       -> "cosmos-1408-debris";
                case "ROCKET BODY"  -> "rocket-body";
                default             -> "active";
            };
        }

        return BASE_URL + "?GROUP=" + groupParam + "&FORMAT=json";
    }

    // -------------------------------------------------------------------------
    // JSON parsing — mirrors SpaceTrackService
    // -------------------------------------------------------------------------

    private List<SatelliteRecord> parseJsonFormat(List<CelestrakGpRecord> gpRecords) {
        List<SatelliteRecord> records = new ArrayList<>();
        for (CelestrakGpRecord gp : gpRecords) {
            SatelliteRecord record = toSatelliteRecord(gp);
            if (record != null) records.add(record);
        }
        log.info("Parsed {} satellite records from CelesTrak JSON", records.size());
        return records;
    }

    private SatelliteRecord toSatelliteRecord(CelestrakGpRecord gp) {
        try {
            return SatelliteRecord.builder()
                    .noradCatId(gp.noradCatId())
                    .name(gp.objectName())
                    .objectType(gp.objectType())
                    .orbitRegime(deriveOrbitRegime(gp))
                    .epoch(parseEpoch(gp.epoch()))
                    .line1(gp.tleLine1())
                    .line2(gp.tleLine2())
                    .source(SOURCE)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to map GP record for NORAD ID {}: {}", gp.noradCatId(), e.getMessage());
            return null;
        }
    }

    private String deriveOrbitRegime(CelestrakGpRecord gp) {
        if (gp.eccentricity() != null && gp.eccentricity() > 0.25) return "HEO";
        if (gp.meanMotion() != null && gp.meanMotion() > 11.25) return "LEO";
        return null;
    }

    private Instant parseEpoch(String epochStr) {
        if (!StringUtils.hasText(epochStr)) return null;
        try {
            return LocalDateTime.parse(epochStr, EPOCH_FORMAT).toInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            log.warn("Failed to parse epoch '{}': {}", epochStr, e.getMessage());
            return null;
        }
    }
}

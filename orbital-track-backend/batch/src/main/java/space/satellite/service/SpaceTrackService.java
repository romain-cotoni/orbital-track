package space.satellite.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import space.satellite.record.SatelliteRecord;
import space.satellite.config.SatelliteJobProperties;
import space.satellite.record.SpaceTrackGpRecord;
import space.satellite.services.SpaceTrackAuthService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static space.satellite.constants.Constants.FORMAT_JSON;
import static space.satellite.constants.Constants.GEO_URL;
import static space.satellite.constants.Constants.HEO_URL;
import static space.satellite.constants.Constants.LEO_URL;
import static space.satellite.constants.Constants.MEO_URL;
import static space.satellite.constants.Constants.NOT_DECAYED_URL;
import static space.satellite.constants.Constants.SPACETRACK_BASE_QUERY_URL;
import static space.satellite.constants.Constants.SPACETRACK_SOURCE;
import static space.satellite.constants.Constants.WITHIN_24_HOURS_URL;
import static space.satellite.constants.Constants.WITHIN_30_DAYS_URL;
import static space.satellite.constants.Constants.WITHIN_180_DAYS_URL;
import static space.satellite.constants.Constants.WITHIN_72_HOURS_URL;

/**
 * Service for fetching satellite GP data in bulk from the Space-Track API.
 * <p>
 * Space-Track is the primary data source, operated by the US Space Force.
 * Responses are fetched in JSON format and deserialized via Jackson.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpaceTrackService {

    /** Space-Track epoch format: microsecond UTC datetime without timezone suffix. */
    private static final DateTimeFormatter EPOCH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");

    private final RestClient spaceTrackRestClient;

    private final SpaceTrackAuthService spaceTrackAuthService;

    /**
     * Fetches all GP records from Space-Track and returns them as {@link SatelliteRecord}s.
     *
     * @param props job properties containing orbit filter, epoch window, and other query params
     */
    public List<SatelliteRecord> fetchAllSatellites(SatelliteJobProperties props) {
        spaceTrackAuthService.login(spaceTrackRestClient);
        List<SpaceTrackGpRecord> gpRecords = fetchGpData(props);
        return parseJsonFormat(gpRecords);
    }

    // -------------------------------------------------------------------------
    // JSON fetch
    // -------------------------------------------------------------------------

    private List<SpaceTrackGpRecord> fetchGpData(SatelliteJobProperties props) {
        String url = buildQueryUrl(props);
        log.info("Fetching GP data from Space-Track: {}", url);

        List<SpaceTrackGpRecord> records = spaceTrackRestClient.get()
                .uri(url)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        log.info("Received {} GP records from Space-Track", records != null ? records.size() : 0);
        return records != null ? records : List.of();
    }

    /**
     * Converts {@link SpaceTrackGpRecord}s (JSON DTO) into {@link SatelliteRecord}s (batch DTO).
     */
    private List<SatelliteRecord> parseJsonFormat(List<SpaceTrackGpRecord> gpRecords) {
        List<SatelliteRecord> records = new ArrayList<>();

        for (SpaceTrackGpRecord gp : gpRecords) {
            SatelliteRecord satelliteRecord = toSatelliteRecord(gp);
            if (satelliteRecord != null) {
                records.add(satelliteRecord);
            }
        }

        log.info("Parsed {} satellite records from Space-Track JSON", records.size());
        return records;
    }

    private SatelliteRecord toSatelliteRecord(SpaceTrackGpRecord gp) {
        try {
            return SatelliteRecord.builder()
                    .noradCatId(Integer.parseInt(gp.noradCatId()))
                    .name(gp.objectName())
                    .objectType(gp.objectType())
                    .countryCode(gp.countryCode())
                    .launchDate(gp.launchDate())
                    .decayDate(gp.decayDate())
                    .rcsSize(gp.rcsSize())
                    .orbitRegime(deriveOrbitRegime(gp))
                    .epoch(parseEpoch(gp.epoch()))
                    .line1(gp.tleLine1())
                    .line2(gp.tleLine2())
                    .source(SPACETRACK_SOURCE)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to map GP record for NORAD ID {}: {}", gp.noradCatId(), e.getMessage());
            return null;
        }
    }


    /**
     * Derives the orbit regime from the GP record's orbital parameters.
     * <p>
     * Uses the same thresholds as the query filters in {@link space.satellite.constants.Constants}:
     * <ul>
     *   <li>HEO: eccentricity &gt; 0.25 (independent of altitude)</li>
     *   <li>LEO: mean motion &gt; 11.25 rev/day (≈ altitude &lt; 2 000 km)</li>
     *   <li>MEO: 1.5 &lt; mean motion ≤ 11.25 (≈ altitude 2 000–24 000 km)</li>
     *   <li>GEO: mean motion ≤ 1.5 (geosynchronous and near-GEO drifters)</li>
     * </ul>
     * Returns {@code null} if mean_motion is missing — the row will be skipped downstream.
     * </p>
     */
    private String deriveOrbitRegime(SpaceTrackGpRecord gp) {
        if (gp.eccentricity() != null && gp.eccentricity() > 0.25) {
            return "HEO";
        }
        if (gp.meanMotion() == null) {
            return null;
        }
        if (gp.meanMotion() > 11.25) {
            return "LEO";
        }
        if (gp.meanMotion() > 1.5) {
            return "MEO";
        }
        return "GEO";

    }

    /** Parses Space-Track's microsecond UTC epoch string to {@link Instant}. */
    private Instant parseEpoch(String epochStr) {
        if (!StringUtils.hasText(epochStr)) {
            return null;
        }
        try {
            return LocalDateTime.parse(epochStr, EPOCH_FORMAT).toInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            log.warn("Failed to parse epoch '{}': {}", epochStr, e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Query URL builder
    // -------------------------------------------------------------------------

    private String buildQueryUrl(SatelliteJobProperties props) {
        StringBuilder url = new StringBuilder(SPACETRACK_BASE_QUERY_URL); // relative path - RestClient will prepend baseUrl

        if (StringUtils.hasText(props.getGroup())) {
            url.append("/OBJECT_TYPE/").append(props.getGroup().replace(" ", "%20"));
        }

        if (StringUtils.hasText(props.getOrbitType())) {
            String orbitSegment = switch (props.getOrbitType().toUpperCase()) {
                case "HEO" -> HEO_URL;
                case "LEO" -> LEO_URL;
                case "MEO" -> MEO_URL;
                case "GEO" -> GEO_URL;
                default -> "";
            };
            url.append(orbitSegment);
        }

        if (StringUtils.hasText(props.getEpochWindow())) {
            String epochSegment = switch (props.getEpochWindow()) {
                case "24h"  -> WITHIN_24_HOURS_URL;
                case "72h"  -> WITHIN_72_HOURS_URL;
                case "30d"  -> WITHIN_30_DAYS_URL;
                case "180d" -> WITHIN_180_DAYS_URL;
                default     -> "";
            };
            url.append(epochSegment);
        }

        if (props.isExcludeDecayed()) {
            url.append(NOT_DECAYED_URL);
        }

        url.append(FORMAT_JSON);
        log.info("Built Space-Track query URL for orbit={}: {}", props.getOrbitType(), url);
        return url.toString();
    }

}

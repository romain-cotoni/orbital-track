package space.satellite.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;
import space.satellite.constants.Constants;
import space.satellite.entities.Satellite;
import space.satellite.record.SatelliteRecord;

import java.time.Duration;
import java.time.Instant;

/**
 * Spring Batch ItemProcessor that validates and transforms satellite records.
 * <p>
 * Performs TLE format validation on each record and converts valid records
 * to {@link Satellite} entities. Also derives constellation and mission type
 * from name and country code. Invalid records are filtered out by returning null.
 * </p>
 */
@Component
@Slf4j
public class SatelliteItemProcessor implements ItemProcessor<SatelliteRecord, Satellite> {

    /**
     * Validates the TLE lines and builds a Satellite entity.
     * Returns null (skip) if TLE lines are missing or malformed.
     */
    @Override
    public Satellite process(SatelliteRecord record) {
        if (!isValidTle(record)) {
            log.warn("Invalid TLE for NORAD ID {}: skipping", record.noradCatId());
            return null;
        }

        if (isTleStale(record)) {
            log.warn("Stale TLE for NORAD ID {} ({}): skipping", record.noradCatId(), record.name());
            return null;
        }

        String constellation = deriveConstellation(record.name(), record.countryCode());
        String missionType   = deriveMissionType(record.name(), constellation);

        return Satellite.builder()
                .noradCatId(record.noradCatId())
                .name(record.name())
                .objectType(record.objectType())
                .countryCode(record.countryCode())
                .launchDate(record.launchDate())
                .decayDate(record.decayDate())
                .rcsSize(record.rcsSize())
                .orbitRegime(record.orbitRegime())
                .epoch(record.epoch())
                .line1(record.line1())
                .line2(record.line2())
                .fetchedAt(Instant.now())
                .source(record.source())
                .constellation(constellation)
                .missionType(missionType)
                .build();
    }

    // -------------------------------------------------------------------------
    // Constellation derivation
    // -------------------------------------------------------------------------

    private static String deriveConstellation(String name, String countryCode) {
        if (name == null) return null;
        String n = name.toUpperCase();

        if (n.contains("STARLINK")) return "Starlink";

        if (n.contains("ONEWEB")) return "OneWeb";

        if (n.contains("IRIDIUM")) return "Iridium";

        if (n.contains("O3B")) return "O3B";

        // BeiDou
        if (n.contains("BEIDOU") || n.contains("COMPASS") && "PRC".equals(countryCode)) return "BeiDou";

        // GPS (US navigation)
        if (n.contains("NAVSTAR") || n.contains("GPS IIF") || n.contains("GPS IIIA") || n.contains("GPS IIR") || n.contains("GPS III")) return "GPS";

        // Galileo — exclude Indian GSAT satellites
        if (n.contains("GALILEO") || (n.startsWith("GSAT") && !"IND".equals(countryCode))) return "Galileo";

        // GLONASS — some are named explicitly, others as COSMOS (handled by name when possible)
        if (n.contains("GLONASS")) return "GLONASS";

        return null;
    }

    // -------------------------------------------------------------------------
    // Mission type derivation
    // -------------------------------------------------------------------------

    private static String deriveMissionType(String name, String constellation) {
        if (name == null) return null;
        String n = name.toUpperCase();

        // Navigation constellations
        if ("GPS".equals(constellation) || "Galileo".equals(constellation) || "GLONASS".equals(constellation) || "BeiDou".equals(constellation)
            || n.contains("NAVSTAR") || n.contains("GALILEO") || n.contains("GLONASS")
            || n.contains("BEIDOU") || n.contains("QZSS") || n.contains("NAVIC") || n.contains("IRNSS"))
            return "NAVIGATION";

        // Internet broadband
        if ("Starlink".equals(constellation) || "OneWeb".equals(constellation) || "Iridium".equals(constellation) || "O3B".equals(constellation)
            || n.contains("TELEDESIC"))
            return "INTERNET";

        // Weather / Meteorology
        if (n.contains("METOP") || n.contains("METEOSAT") || n.contains("HIMAWARI")
            || n.startsWith("FY-") || n.startsWith("NOAA") || n.startsWith("GOES") || n.startsWith("METEOR-M")
            || n.startsWith("DMSP") || n.startsWith("MSG ") || n.startsWith("MTG") || n.contains("ELEKTRO-L"))
            return "WEATHER";

        // Earth observation
        if (n.contains("LANDSAT") || n.contains("SENTINEL") || n.contains("PLEIADES") || n.contains("WORLDVIEW")
            || n.contains("GEOEYE") || n.contains("TERRASAR") || n.contains("COSMO-SKYMED") || n.contains("RADARSAT")
            || n.contains("KOMPSAT") || n.contains("CARTOSAT") || n.contains("RESOURCESAT") || n.contains("RISAT")
            || n.startsWith("SPOT-") || n.startsWith("EROS "))
            return "EARTH_OBS";

        // Communication / Telecom
        if (n.contains("INMARSAT") || n.contains("THURAYA") || n.contains("BADR-")
            || n.contains("VIASAT") || n.contains("ECHOSTAR") || n.contains("DIRECTV")
            || n.contains("GALAXY ") || n.contains("OPTUS") || n.contains("APSTAR") || n.contains("ASIASAT")
            || n.contains("ARABSAT") || n.contains("HOTBIRD") || n.contains("HORIZONS") || n.contains("SKNET")
            || n.startsWith("AMC-") || n.startsWith("NSS-") || n.startsWith("INTELSAT") || n.startsWith("EUTELSAT")
            || n.startsWith("SES-") || n.startsWith("ASTRA ") || n.startsWith("THOR ") || n.startsWith("IS-"))
            return "COMMUNICATION";

        // Astronomy / space telescopes
        if (n.contains("HUBBLE") || n.contains("CHANDRA") || n.contains("XMM")
            || n.contains("INTEGRAL") || n.contains("CHEOPS") || n.contains("FERMI")
            || n.contains("TESS") || n.contains("NUSTAR") || n.contains("NEOWISE")
            || n.startsWith("SWIFT"))
            return "SCIENCE";

        // Earth science
        if (n.contains("SWARM") || n.contains("CRYOSAT") || n.contains("ICESAT")
            || n.contains("JASON") || n.contains("CALIPSO") || n.contains("CLOUDSAT")
            || n.contains("SMOS") || n.startsWith("GRACE") || n.startsWith("AQUA ")
            || n.startsWith("TERRA ") || n.startsWith("AURA ") || n.startsWith("GPM "))
            return "SCIENCE";

        // Solar / space physics
        if (n.contains("THEMIS") || n.contains("STEREO")
            || n.startsWith("CLUSTER") || n.startsWith("SDO ") || n.startsWith("MMS "))
            return "SCIENCE";

        return null;
    }

    // -------------------------------------------------------------------------

    private boolean isTleStale(SatelliteRecord record) {
        if (record.epoch() == null) return true;
        Duration maxAge = "GEO".equals(record.orbitRegime()) ? Constants.MAX_TLE_AGE_GEO : Constants.MAX_TLE_AGE;
        return Duration.between(record.epoch(), Instant.now()).compareTo(maxAge) > 0;
    }

    private boolean isValidTle(SatelliteRecord record) {
        if (record == null) return false;
        if (record.noradCatId() == null || record.noradCatId() <= 0) return false;
        if (record.line1() == null || record.line1().length() < 69) return false;
        if (record.line2() == null || record.line2().length() < 69) return false;
        if (!record.line1().startsWith("1 ")) return false;
        if (!record.line2().startsWith("2 ")) return false;
        return true;
    }
}

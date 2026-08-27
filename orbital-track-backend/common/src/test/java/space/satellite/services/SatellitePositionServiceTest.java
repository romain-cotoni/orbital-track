package space.satellite.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import space.satellite.dtos.SatellitePosition;
import space.satellite.entities.Satellite;
import space.satellite.repositories.SatelliteRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Orekit-based propagation engine.
 *
 * Strategy: TLE strings are generated programmatically from ISS orbital elements
 * after Orekit initialisation. This avoids manual checksum arithmetic and
 * guarantees that the strings fed to the production code are well-formed.
 */
@ExtendWith(MockitoExtension.class)
class SatellitePositionServiceTest {

    private static final int ISS_NORAD_ID = 25544;

    // Populated in @BeforeEach, after Orekit is ready
    private String issLine1;
    private String issLine2;
    private Instant issEpoch;

    @Mock SatelliteRepository satelliteRepository;
    @Mock TleServiceFactory    tleServiceFactory;

    @InjectMocks SatellitePositionService service;

    @BeforeEach
    void setup() {
        // 1. Boot Orekit (equivalent to @PostConstruct in production)
        service.initialize();

        // 2. Build an ISS-like TLE from orbital elements.
        //    Using the programmatic constructor means Orekit generates correctly
        //    formatted + checksummed line1/line2 strings for us.
        AbsoluteDate epoch = new AbsoluteDate(2023, 9, 5, 12, 0, 0.0, TimeScalesFactory.getUTC());
        TLE issTle = new TLE(
            ISS_NORAD_ID,                           // NORAD catalog number
            'U',                                    // classification
            98,                                     // international designator: launch year
            67,                                     // international designator: launch number
            "A",                                    // international designator: piece
            0,                                      // ephemeris type (SGP4)
            999,                                    // element set number
            epoch,
            15.50 * 2 * Math.PI / 86400,           // mean motion  (rad/s ≈ 15.5 rev/day)
            1.5e-9,                                 // Ṁ  (first derivative, rad/s²)
            0.0,                                    // M̈  (second derivative)
            0.0006,                                 // eccentricity (~circular)
            Math.toRadians(51.6416),                // inclination
            Math.toRadians(127.63),                 // argument of perigee
            Math.toRadians(270.40),                 // RAAN
            Math.toRadians(232.54),                 // mean anomaly
            41373,                                  // revolution number at epoch
            1.5e-5                                  // BSTAR drag term
        );

        issLine1  = issTle.getLine1();
        issLine2  = issTle.getLine2();
        issEpoch  = epoch.toDate(TimeScalesFactory.getUTC()).toInstant();
    }


    // =========================================================================
    // Orekit initialisation
    // =========================================================================

    @Test
    void initialize_orekitFramesAreReady_propagationDoesNotThrow() {
        // If initialize() failed, propagate() would NPE before even hitting the repo.
        // A successful call proves earth + itrf were wired up correctly.
        when(satelliteRepository.findByNoradCatId(ISS_NORAD_ID)).thenReturn(Optional.of(issEntity()));

        assertThat(service.propagate(ISS_NORAD_ID, Instant.now())).isPresent();
    }


    // =========================================================================
    // propagate — ISS physical sanity checks
    // =========================================================================

    @Test
    void propagate_ISS_altitudeIsInLEORange() {
        when(satelliteRepository.findByNoradCatId(ISS_NORAD_ID)).thenReturn(Optional.of(issEntity()));

        SatellitePosition pos = service.propagate(ISS_NORAD_ID, Instant.now()).orElseThrow();

        // ISS cruises at ~420 km; generous band to absorb TLE age drift
        assertThat(pos.altM())
            .as("ISS altitude should be within LEO range (250–500 km)")
            .isBetween(250_000.0, 500_000.0);
    }

    @Test
    void propagate_ISS_latitudeIsWithinInclination() {
        when(satelliteRepository.findByNoradCatId(ISS_NORAD_ID)).thenReturn(Optional.of(issEntity()));

        SatellitePosition pos = service.propagate(ISS_NORAD_ID, Instant.now()).orElseThrow();

        // ISS inclination is 51.6° — geodetic latitude never exceeds ±52°
        assertThat(Math.abs(pos.latDeg()))
            .as("ISS latitude must not exceed its orbital inclination (~51.6°)")
            .isLessThanOrEqualTo(52.0);
    }

    @Test
    void propagate_ISS_longitudeIsValid() {
        when(satelliteRepository.findByNoradCatId(ISS_NORAD_ID)).thenReturn(Optional.of(issEntity()));

        SatellitePosition pos = service.propagate(ISS_NORAD_ID, Instant.now()).orElseThrow();

        assertThat(pos.lonDeg())
            .as("Longitude must be in [-180, 180]")
            .isBetween(-180.0, 180.0);
    }

    @Test
    void propagate_ISS_orbitalSpeedIsPhysical() {
        when(satelliteRepository.findByNoradCatId(ISS_NORAD_ID)).thenReturn(Optional.of(issEntity()));

        SatellitePosition pos = service.propagate(ISS_NORAD_ID, Instant.now()).orElseThrow();

        double speed = Math.sqrt(pos.vxMs() * pos.vxMs() + pos.vyMs() * pos.vyMs() + pos.vzMs() * pos.vzMs());

        // ISS orbital speed ~7660 m/s; allow ±500 m/s for TLE age and rounding
        assertThat(speed)
            .as("ISS ITRF speed should be ~7660 m/s")
            .isBetween(7_000.0, 8_200.0);
    }

    @Test
    void propagate_ISS_orbitRegimeIsLEO() {
        when(satelliteRepository.findByNoradCatId(ISS_NORAD_ID)).thenReturn(Optional.of(issEntity()));

        SatellitePosition pos = service.propagate(ISS_NORAD_ID, Instant.now()).orElseThrow();

        assertThat(pos.orbitRegime()).isEqualTo("LEO");
    }

    @Test
    void propagate_ISS_noradIdIsPreserved() {
        when(satelliteRepository.findByNoradCatId(ISS_NORAD_ID)).thenReturn(Optional.of(issEntity()));

        SatellitePosition pos = service.propagate(ISS_NORAD_ID, Instant.now()).orElseThrow();

        assertThat(pos.noradId()).isEqualTo(ISS_NORAD_ID);
    }

    @Test
    void propagate_ISS_propagatedAtMatchesRequestedTime() {
        when(satelliteRepository.findByNoradCatId(ISS_NORAD_ID)).thenReturn(Optional.of(issEntity()));
        Instant t = Instant.parse("2024-01-15T10:30:00Z");

        SatellitePosition pos = service.propagate(ISS_NORAD_ID, t).orElseThrow();

        assertThat(pos.propagatedAt()).isEqualTo(t);
    }


    // =========================================================================
    // propagate — cache hit path
    // =========================================================================

    @Test
    void propagate_secondCall_usesCache_doesNotQueryDb() {
        when(satelliteRepository.findByNoradCatId(ISS_NORAD_ID)).thenReturn(Optional.of(issEntity()));

        service.propagate(ISS_NORAD_ID, Instant.now()); // primes the cache
        service.propagate(ISS_NORAD_ID, Instant.now()); // should hit cache

        // If the repo were queried twice Mockito would record two calls; we expect exactly one
        org.mockito.Mockito.verify(satelliteRepository, org.mockito.Mockito.times(1))
            .findByNoradCatId(ISS_NORAD_ID);
    }


    // =========================================================================
    // propagate — guard / edge cases
    // =========================================================================

    @Test
    void propagate_satelliteNotInDatabase_returnsEmpty() {
        when(satelliteRepository.findByNoradCatId(99999)).thenReturn(Optional.empty());

        assertThat(service.propagate(99999, Instant.now())).isEmpty();
    }

    @Test
    void propagate_satelliteHasNullTle_returnsEmpty() {
        Satellite noTle = Satellite.builder()
            .noradCatId(12345)
            .name("DUMMY")
            .line1(null)
            .line2(null)
            .build();
        when(satelliteRepository.findByNoradCatId(12345)).thenReturn(Optional.of(noTle));

        assertThat(service.propagate(12345, Instant.now())).isEmpty();
    }

    @Test
    void propagate_satelliteHasInvalidTle_returnsEmpty() {
        Satellite bad = Satellite.builder()
            .noradCatId(11111)
            .name("BAD-TLE")
            .line1("this is not a valid TLE line 1 at all")
            .line2("this is not a valid TLE line 2 at all")
            .build();
        when(satelliteRepository.findByNoradCatId(11111)).thenReturn(Optional.of(bad));

        assertThat(service.propagate(11111, Instant.now())).isEmpty();
    }


    // =========================================================================
    // rebuildCache
    // =========================================================================

    @Test
    void rebuildCache_logsSuccess_andCacheIsPopulated() {
        when(satelliteRepository.findAll()).thenReturn(List.of(issEntity()));

        service.rebuildCache();

        // After rebuild the regime index is populated: propagateByRegime goes through
        // the index, not the DB, so findAllByOrbitRegime is never called here.
        List<SatellitePosition> positions = service.propagateByRegime("LEO", Instant.now());
        assertThat(positions).hasSize(1);
        assertThat(positions.getFirst().noradId()).isEqualTo(ISS_NORAD_ID);
    }

    @Test
    void rebuildCache_emptyDatabase_producesNoPositions() {
        when(satelliteRepository.findAll()).thenReturn(List.of());
        when(satelliteRepository.findAllByOrbitRegime("LEO")).thenReturn(List.of());

        service.rebuildCache();

        assertThat(service.propagateByRegime("LEO", Instant.now())).isEmpty();
    }

    @Test
    void rebuildCache_invalidTleInDb_isSkippedGracefully() {
        Satellite bad = Satellite.builder()
            .noradCatId(77777)
            .orbitRegime("LEO")
            .line1("garbage line 1")
            .line2("garbage line 2")
            .build();
        when(satelliteRepository.findAll()).thenReturn(List.of(issEntity(), bad));

        service.rebuildCache();

        List<SatellitePosition> positions = service.propagateByRegime("LEO", Instant.now());
        assertThat(positions).hasSize(1);
        assertThat(positions.getFirst().noradId()).isEqualTo(ISS_NORAD_ID);
    }


    // =========================================================================
    // propagateByRegime
    // =========================================================================

    @Test
    void propagateByRegime_indexNotBuilt_fallsBackToDbQuery() {
        // No rebuildCache() — regime index is empty
        when(satelliteRepository.findAllByOrbitRegime("LEO")).thenReturn(List.of(issEntity()));
        when(satelliteRepository.findByNoradCatId(ISS_NORAD_ID)).thenReturn(Optional.of(issEntity()));

        List<SatellitePosition> positions = service.propagateByRegime("LEO", Instant.now());

        assertThat(positions).hasSize(1);
        assertThat(positions.getFirst().orbitRegime()).isEqualTo("LEO");
    }

    @Test
    void propagateByRegime_unknownRegime_returnsEmptyList() {
        when(satelliteRepository.findAllByOrbitRegime("GEO")).thenReturn(List.of());

        assertThat(service.propagateByRegime("GEO", Instant.now())).isEmpty();
    }

    @Test
    void propagateByRegime_afterRebuild_multipleRegimesAreIsolated() {
        Satellite meoSat = Satellite.builder()
            .noradCatId(28654)
            .name("GPS IIR-1")
            .orbitRegime("MEO")
            .line1(issLine1)  // re-use ISS TLE shape — this is a structure test, not a physics test
            .line2(issLine2)
            .fetchedAt(Instant.now())
            .build();

        when(satelliteRepository.findAll()).thenReturn(List.of(issEntity(), meoSat));
        service.rebuildCache();

        List<SatellitePosition> leoPositions = service.propagateByRegime("LEO", Instant.now());
        List<SatellitePosition> meoPositions = service.propagateByRegime("MEO", Instant.now());

        assertThat(leoPositions).hasSize(1);
        assertThat(leoPositions.getFirst().noradId()).isEqualTo(ISS_NORAD_ID);
        assertThat(meoPositions).hasSize(1);
        assertThat(meoPositions.getFirst().noradId()).isEqualTo(28654);
    }


    // =========================================================================
    // Helper
    // =========================================================================

    private Satellite issEntity() {
        return Satellite.builder()
            .noradCatId(ISS_NORAD_ID)
            .name("ISS (ZARYA)")
            .orbitRegime("LEO")
            .line1(issLine1)
            .line2(issLine2)
            .fetchedAt(Instant.now())
            .build();
    }
}

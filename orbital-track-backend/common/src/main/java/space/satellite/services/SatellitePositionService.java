package space.satellite.services;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.net.URL;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.orekit.bodies.BodyShape;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.data.ClasspathCrawler;
import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.DirectoryCrawler;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.Transform;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.PVCoordinates;
import org.springframework.stereotype.Service;
import space.satellite.caches.CachedSatelliteData;
import space.satellite.caches.SatelliteKey;
import space.satellite.dtos.SatellitePosition;
import space.satellite.dtos.SatelliteRequestDto;
import space.satellite.dtos.SatelliteResponseDto;
import space.satellite.entities.Satellite;
import space.satellite.repositories.SatelliteRepository;

import static space.satellite.constants.Constants.MAX_TLE_AGE;
import static space.satellite.constants.Constants.OREKIT_DATA_CLASSPATH;
import static space.satellite.constants.Constants.OREKIT_DATA_DIRECTORY;
import static space.satellite.constants.Constants.SPACETRACK_SOURCE;
import static space.satellite.constants.Constants.TLE_CACHE_DURATION;


@Service
@Slf4j
@RequiredArgsConstructor
public class SatellitePositionService implements PropagatorCacheService {

    @Value("${orekit.data.path:#{null}}")
    private String orekitDataPath;

    private TleService tleService;

    private final TleServiceFactory tleServiceFactory;

    private final SatelliteRepository satelliteRepository;

    // Earth ellipsoid (WGS84) and Earth-fixed frame
    private BodyShape earth;
    private Frame itrf;

    // Propagator cache keyed by NORAD ID
    private final Map<SatelliteKey, CachedSatelliteData> satelliteCache = new ConcurrentHashMap<>();

    // Regime index: orbitRegime → set of NORAD IDs (rebuilt atomically by rebuildCache)
    private volatile ConcurrentHashMap<String, Set<Integer>> regimeIndex = new ConcurrentHashMap<>();

    // Reverse lookup: noradId → orbitRegime (volatile reference, CHM internals are thread-safe)
    private volatile ConcurrentHashMap<Integer, String> noradToRegime = new ConcurrentHashMap<>();


    // -------------------------------------------------------------------------
    // Step 1 — Orekit init
    // -------------------------------------------------------------------------

    @PostConstruct
    public void initialize() {
        DataProvidersManager manager = DataContext.getDefault().getDataProvidersManager();

        // 1. Explicit path from property (highest priority — works for all deployment scenarios)
        if (orekitDataPath != null && !orekitDataPath.isBlank()) {
            File configured = new File(orekitDataPath);
            if (configured.exists() && configured.isDirectory()) {
                manager.addProvider(new DirectoryCrawler(configured));
                log.info("Orekit data loaded from configured path: {}", configured.getAbsolutePath());
                initEarthBodyShape();
                return;
            }
            log.warn("orekit.data.path configured but not found: {}", orekitDataPath);
        }

        // 2. Well-known relative path (works when CWD is project root — e.g. batch module)
        File relativeDir = new File(OREKIT_DATA_DIRECTORY);
        if (relativeDir.exists() && relativeDir.isDirectory()) {
            manager.addProvider(new DirectoryCrawler(relativeDir));
            log.info("Orekit data loaded from relative directory: {}", relativeDir.getAbsolutePath());
            initEarthBodyShape();
            return;
        }

        // 3. Classpath (works in unit tests where target/classes/orekit-data is a real directory)
        URL resourceUrl = getClass().getClassLoader().getResource(OREKIT_DATA_CLASSPATH);
        if (resourceUrl != null && "file".equals(resourceUrl.getProtocol())) {
            try {
                File classpathDir = new File(resourceUrl.toURI());
                manager.addProvider(new DirectoryCrawler(classpathDir));
                log.info("Orekit data loaded from classpath directory: {}", classpathDir.getAbsolutePath());
            } catch (java.net.URISyntaxException e) {
                throw new IllegalStateException("Invalid orekit-data classpath URL: " + resourceUrl, e);
            }
        } else {
            manager.addProvider(new ClasspathCrawler(OREKIT_DATA_CLASSPATH));
            log.info("Orekit data loaded from classpath: {}", OREKIT_DATA_CLASSPATH);
        }

        initEarthBodyShape();
    }

    private void initEarthBodyShape() {
        itrf = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
        earth = new OneAxisEllipsoid(6378137.0,       // equatorial radius (m)
                                     1.0 / 298.257223563, // WGS84 flattening
                                     itrf);
    }


    // -------------------------------------------------------------------------
    // Step 2 — Single satellite propagation (internal, DB-backed)
    // -------------------------------------------------------------------------

    /**
     * Propagates a single satellite to the given time using the DB-backed cache.
     * Thread-safe: creates a fresh TLEPropagator per call from the immutable cached TLE.
     *
     * @return empty if the satellite is not found in DB, has no TLE, or has a hyperbolic orbit (e ≥ 1.0)
     */
    public Optional<SatellitePosition> propagate(int noradId, Instant time) {
        SatelliteKey key = SatelliteKey.norad(noradId);
        CachedSatelliteData cached = satelliteCache.get(key);

        TLE tle;
        String orbitRegime;

        if (cached != null && !cached.isExpired(TLE_CACHE_DURATION)) {
            tle = cached.tle();
            orbitRegime = noradToRegime.get(noradId);
        } else {
            Optional<Satellite> satOpt = satelliteRepository.findByNoradCatId(noradId);
            if (satOpt.isEmpty()) {
                log.debug("Satellite {} not found in database", noradId);
                return Optional.empty();
            }
            Satellite sat = satOpt.get();

            if (sat.getLine1() == null || sat.getLine2() == null) {
                log.warn("NORAD {} has no TLE data", noradId);
                return Optional.empty();
            }

            try {
                tle = new TLE(sat.getLine1(), sat.getLine2());
            } catch (Exception e) {
                log.warn("Invalid TLE for NORAD {}: {}", noradId, e.getMessage());
                return Optional.empty();
            }

            if (tle.getE() >= 1.0) {
                log.debug("Skipping hyperbolic orbit for NORAD {} (e={})", noradId, tle.getE());
                return Optional.empty();
            }

            Instant tleEpoch = tle.getDate().toDate(TimeScalesFactory.getUTC()).toInstant();
            if (java.time.Duration.between(tleEpoch, Instant.now()).compareTo(MAX_TLE_AGE) > 0) {
                log.debug("Skipping stale TLE for NORAD {} (epoch: {})", noradId, tleEpoch);
                return Optional.empty();
            }

            try {
                satelliteCache.put(key, new CachedSatelliteData(tle, TLEPropagator.selectExtrapolator(tle), Instant.now()));
            } catch (Exception e) {
                log.warn("Propagation failed for NORAD {} at {}: {}", noradId, time, e.getMessage());
                return Optional.empty();
            }
            orbitRegime = sat.getOrbitRegime();
            if (orbitRegime != null) {
                noradToRegime.put(noradId, orbitRegime);
            }
        }

        // Always create a fresh propagator — TLEPropagator is NOT thread-safe
        try {
            TLEPropagator propagator = TLEPropagator.selectExtrapolator(tle);
            AbsoluteDate propagationTime = new AbsoluteDate(time, TimeScalesFactory.getUTC());
            Frame eme2000          = FramesFactory.getEME2000();
            PVCoordinates pvEme2000 = propagator.getPVCoordinates(propagationTime, eme2000);
            Transform transform    = eme2000.getTransformTo(itrf, propagationTime);
            PVCoordinates pvItrf   = transform.transformPVCoordinates(pvEme2000);
            GeodeticPoint geoPoint = earth.transform(pvItrf.getPosition(), itrf, propagationTime);

            double lat = Math.toDegrees(geoPoint.getLatitude());
            double lon = Math.toDegrees(geoPoint.getLongitude());
            double alt = geoPoint.getAltitude();
            double vx  = pvItrf.getVelocity().getX();
            double vy  = pvItrf.getVelocity().getY();
            double vz  = pvItrf.getVelocity().getZ();

            if (!Double.isFinite(lat) || !Double.isFinite(lon) || !Double.isFinite(alt)
                    || !Double.isFinite(vx) || !Double.isFinite(vy) || !Double.isFinite(vz)) {
                log.warn("Non-finite position for NORAD {} at {}: lat={}, lon={}, alt={}", noradId, time, lat, lon, alt);
                return Optional.empty();
            }

            return Optional.of(new SatellitePosition(
                noradId,
                orbitRegime,
                lat, lon, alt, vx, vy, vz,
                time
            ));
        } catch (Exception e) {
            log.warn("Propagation failed for NORAD {} at {}: {}", noradId, time, e.getMessage());
            return Optional.empty();
        }
    }


    // -------------------------------------------------------------------------
    // Step 3 — Bulk propagation by orbit regime (what WebSocket will call)
    // -------------------------------------------------------------------------

    /**
     * Propagates all satellites belonging to the given orbit regime at the given time.
     * Falls back to a DB query if the regime index has not been built yet (e.g. before first batch run).
     * Uses a parallel stream — safe because each call to {@link #propagate} creates a fresh propagator.
     *
     * @param orbitRegime "LEO", "MEO", "HEO", or "GEO"
     */
    public List<SatellitePosition> propagateByRegime(String orbitRegime, Instant time) {
        Set<Integer> noradIds = regimeIndex.get(orbitRegime);

        if (noradIds == null || noradIds.isEmpty()) {
            log.debug("Regime index empty for {}. Falling back to DB query.", orbitRegime);
            noradIds = satelliteRepository.findAllByOrbitRegime(orbitRegime).stream()
                                          .map(Satellite::getNoradCatId)
                                          .collect(Collectors.toSet());
        }

        return noradIds.parallelStream()
                       .map(id -> propagate(id, time))
                       .filter(Optional::isPresent)
                       .map(Optional::get)
                       .toList();
    }


    // -------------------------------------------------------------------------
    // Cache rebuild (called by batch after each job completes)
    // -------------------------------------------------------------------------

    @Override
    public void rebuildCache() {
        log.info("Rebuilding propagator cache from database TLEs...");
        int previousSize = satelliteCache.size();
        satelliteCache.clear();

        List<Satellite> allSatellites = satelliteRepository.findAll();
        int successCount = 0;
        int skipCount = 0;
        int errorCount = 0;

        Map<String, Set<Integer>> newRegimeIndex = new HashMap<>();
        Map<Integer, String> newNoradToRegime = new HashMap<>();

        for (Satellite sat : allSatellites) {
            try {
                TLE orekitTle = new TLE(sat.getLine1(), sat.getLine2());

                if (orekitTle.getE() >= 1.0) {
                    log.warn("Skipping hyperbolic orbit for NORAD {} (e={})", sat.getNoradCatId(), orekitTle.getE());
                    skipCount++;
                    continue;
                }

                Instant tleEpoch = orekitTle.getDate().toDate(TimeScalesFactory.getUTC()).toInstant();
                if (java.time.Duration.between(tleEpoch, Instant.now()).compareTo(MAX_TLE_AGE) > 0) {
                    log.debug("Skipping stale TLE for NORAD {} (epoch: {})", sat.getNoradCatId(), tleEpoch);
                    skipCount++;
                    continue;
                }

                SatelliteKey key = SatelliteKey.norad(sat.getNoradCatId());
                Instant fetchTime = sat.getFetchedAt() != null ? sat.getFetchedAt() : Instant.now();
                satelliteCache.put(key, new CachedSatelliteData(orekitTle, TLEPropagator.selectExtrapolator(orekitTle), fetchTime));

                String regime = sat.getOrbitRegime();
                if (regime != null) {
                    newRegimeIndex.computeIfAbsent(regime, k -> new HashSet<>()).add(sat.getNoradCatId());
                    newNoradToRegime.put(sat.getNoradCatId(), regime);
                }
                successCount++;
            } catch (Exception e) {
                log.warn("Failed to create propagator for NORAD {}: {}", sat.getNoradCatId(), e.getMessage());
                errorCount++;
            }
        }

        // Atomic swap — concurrent readers see either the old or new complete map, never a partial state
        regimeIndex = new ConcurrentHashMap<>(newRegimeIndex);
        noradToRegime = new ConcurrentHashMap<>(newNoradToRegime);

        log.info("Cleared {} entries. Cache rebuilt: {} loaded, {} hyperbolic skipped, {} errors", previousSize, successCount, skipCount, errorCount);
    }


    // -------------------------------------------------------------------------
    // Ground track (REST API — Phase 2)
    // -------------------------------------------------------------------------

    /**
     * Produces a time-series of geodetic positions along a satellite's ground track.
     * Samples are spaced {@code interval} apart, starting at {@code start} and covering {@code duration}.
     *
     * @param noradId  NORAD Catalog ID
     * @param start    UTC start time
     * @param duration total duration to cover
     * @param interval sampling interval (e.g. 30 s)
     * @return ordered list of positions; empty if the satellite is unknown or has no valid TLE
     */
    public List<SatellitePosition> propagateGroundTrack(int noradId, Instant start,
                                                        java.time.Duration duration,
                                                        java.time.Duration interval) {
        long steps = duration.toSeconds() / interval.toSeconds();
        List<SatellitePosition> track = new java.util.ArrayList<>((int) steps + 1);
        for (long i = 0; i <= steps; i++) {
            Instant t = start.plus(interval.multipliedBy(i));
            propagate(noradId, t).ifPresent(track::add);
        }
        return track;
    }


    // -------------------------------------------------------------------------
    // REST API path (uses TleService for on-demand TLE fetch, not DB)
    // -------------------------------------------------------------------------

    public SatelliteResponseDto getPosition(SatelliteRequestDto request) {
        SatelliteResponseDto.SatelliteResponseDtoBuilder responseDto = SatelliteResponseDto.builder();

        tleService = tleServiceFactory.getTleService(SPACETRACK_SOURCE);
        int catalogNumber = Integer.parseInt(request.getIdentifier());
        SatelliteKey key = SatelliteKey.norad(catalogNumber);
        CachedSatelliteData cached = satelliteCache.get(key);

        TLE tle;
        if (cached != null && !cached.isExpired(TLE_CACHE_DURATION)) {
            tle = cached.tle();
            responseDto.isCached(true);
            log.info("Using cached TLE (age: {} min)", cached.getAgeMinutes());
        } else {
            tle = tleService.fetchTle(catalogNumber);
            satelliteCache.put(key, new CachedSatelliteData(tle, TLEPropagator.selectExtrapolator(tle), Instant.now()));
            log.info("Fetched new TLE for satellite {}", catalogNumber);
        }

        TLEPropagator propagator = TLEPropagator.selectExtrapolator(tle);
        AbsoluteDate now           = new AbsoluteDate(Instant.now(), TimeScalesFactory.getUTC());
        Frame eme2000              = FramesFactory.getEME2000();
        PVCoordinates pvEme2000    = propagator.getPVCoordinates(now, eme2000);
        Transform transform        = eme2000.getTransformTo(itrf, now);
        PVCoordinates pvItrf       = transform.transformPVCoordinates(pvEme2000);
        GeodeticPoint geoPoint     = earth.transform(pvItrf.getPosition(), itrf, now);

        responseDto.name(request.getName());
        responseDto.identifier(request.getIdentifier());
        responseDto.tleEpoch(tle.getDate().toString(TimeScalesFactory.getUTC()));
        responseDto.tleLine1(tle.getLine1());
        responseDto.tleLine2(tle.getLine2());
        responseDto.geodeticLatitude(Math.toDegrees(geoPoint.getLatitude()));
        responseDto.geodeticLongitude(Math.toDegrees(geoPoint.getLongitude()));
        responseDto.geodeticAltitude(geoPoint.getAltitude());
        responseDto.geodeticSpeed(pvItrf.getVelocity().getNorm());
        responseDto.cartesianEme2000X(pvEme2000.getPosition().getX());
        responseDto.cartesianEme2000Y(pvEme2000.getPosition().getY());
        responseDto.cartesianEme2000Z(pvEme2000.getPosition().getZ());
        responseDto.cartesianItrfX(pvItrf.getPosition().getX());
        responseDto.cartesianItrfY(pvItrf.getPosition().getY());
        responseDto.cartesianItrfZ(pvItrf.getPosition().getZ());

        return responseDto.build();
    }

    public List<SatelliteResponseDto> getPositions(List<SatelliteRequestDto> satellites, String tleProvider) {
        tleService = tleServiceFactory.getTleService(tleProvider);
        return satellites.stream().map(this::getPosition).toList();
    }
}

package space.satellite.services;

/**
 * Interface for propagator cache operations.
 * <p>
 * Implemented by {@link SatellitePositionService} to allow batch jobs
 * to trigger cache rebuilds after TLE updates without tight coupling
 * between modules.
 * </p>
 */
public interface PropagatorCacheService {

    /**
     * Clears and rebuilds the propagator cache from database TLEs.
     * <p>
     * This method loads all TLEs from the database, creates Orekit
     * TLEPropagator instances for each, and stores them in the in-memory
     * cache for fast position calculations.
     * </p>
     */
    void rebuildCache();
}

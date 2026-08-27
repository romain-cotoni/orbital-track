package space.satellite.services;

import org.orekit.propagation.analytical.tle.TLE;

public interface TleService {

    TLE fetchTle(int catalogNumber);

}

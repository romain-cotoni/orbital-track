package space.satellite.services;

import org.orekit.propagation.analytical.tle.TLE;
import org.springframework.stereotype.Service;

@Service
public class TleCelestrackService implements TleService {

    @Override
    public TLE fetchTle(int catalogNumber) {
        return null;
    }



}

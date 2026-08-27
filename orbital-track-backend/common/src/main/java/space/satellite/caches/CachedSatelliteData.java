package space.satellite.caches;

import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.propagation.analytical.tle.TLEPropagator;

import java.time.Duration;
import java.time.Instant;

public record CachedSatelliteData(TLE tle, TLEPropagator tlePropagator, Instant tleFetchTime) {

    public boolean isExpired(Duration cacheDuration) {
        return Duration.between(tleFetchTime, Instant.now()).compareTo(cacheDuration) > 0;
    }

    public long getAgeMinutes() {
        return Duration.between(tleFetchTime, Instant.now()).toMinutes();
    }

}

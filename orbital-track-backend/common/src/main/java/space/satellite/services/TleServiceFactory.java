package space.satellite.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TleServiceFactory {

    private final TleService spaceTrackService;

    private final TleService celestrackService;


    public TleServiceFactory(TleSpaceTrackService spaceTrackService, TleCelestrackService celestrackService) {
        this.spaceTrackService = spaceTrackService;
        this.celestrackService = celestrackService;
    }

    public TleService getTleService(String provider) {
        switch (provider.toLowerCase()) {
            case "spacetrack" -> {
                log.debug("Using Tle SpaceTrack provider");
                return spaceTrackService;
            }
            case "celestrak" -> {
                log.debug("Using Tle Celestrak provider");
                return celestrackService;
            }
            default -> {
                log.warn("Unknown provider '{}', defaulting to Tle SpaceTrack provider", provider);
                return spaceTrackService;
            }
        }
    }
}

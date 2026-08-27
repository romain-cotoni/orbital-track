package space.satellite.services;


import lombok.extern.slf4j.Slf4j;
import org.orekit.propagation.analytical.tle.TLE;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import space.satellite.exceptions.TleException;

import static space.satellite.constants.Constants.SPACETRACK_BASE_QUERY_URL;

@Service
@Slf4j
public class TleSpaceTrackService implements TleService {

    private final RestClient restClient;
    private final SpaceTrackAuthService spaceTrackAuthService;

    public TleSpaceTrackService(@Qualifier("spaceTrackRestClient") RestClient restClient, SpaceTrackAuthService spaceTrackAuthService) {
        this.restClient = restClient;
        this.spaceTrackAuthService = spaceTrackAuthService;
    }


    @Override
    public TLE fetchTle(int catalogNumber) {

        // Step 1: Login and retrieve session cookie
        String cookie = spaceTrackAuthService.login(restClient);

        // Step 2: Fetch TLE using the session cookie (could be null if session already active via cookie jar)
        String tleBody = getTle(catalogNumber, cookie);

        // Step 3: Parse TLE
        String[] lines = parseTle(tleBody);

        return new TLE(lines[1].trim(), lines[2].trim());

    }

    private String getTle(int catalogNumber, String cookie) {
        var request = restClient.get().uri(String.format(SPACETRACK_BASE_QUERY_URL, catalogNumber)); // relative path - RestClient prepends baseUrl

        if (StringUtils.hasText(cookie)) {
            request = request.header(HttpHeaders.COOKIE, cookie);
        }

        String tleBody = request.retrieve().body(String.class);

        if (!StringUtils.hasText(tleBody)) {
            throw new TleException("SpaceTrack TLE empty response");
        }

        return tleBody;
    }

    private String[] parseTle(String tleBody) {
        String[] lines = tleBody.trim().split("\n");

        if (lines.length < 3) {
            throw new TleException("SpaceTrack TLE invalid parsed response");
        }

        return lines;
    }

}

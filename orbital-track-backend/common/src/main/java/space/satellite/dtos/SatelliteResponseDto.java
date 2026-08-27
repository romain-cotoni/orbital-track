package space.satellite.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Builder;

import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SatelliteResponseDto {

    // Satellite
    private String name;

    private String identifier;

    private String catalog;

    private String description;


    // TLE
    private String tleEpoch;

    private String tleLine1;

    private String tleLine2;

    private LocalDateTime dateTime;

    private Boolean isCached;


    // Geodetic Coordinates/Geographic Coordinates
    private Double geodeticLatitude;

    private Double geodeticLongitude;

    private Double geodeticAltitude;

    private Double geodeticSpeed;


    // Inertial - doesn't rotate with earth : Fixed to stars, not Earth Axes never move. Good to calculate orbits (physics in inertial frame)
    private Double cartesianEme2000X;

    private Double cartesianEme2000Y;

    private Double cartesianEme2000Z;


    // Earth-fixed - rotates with Earth : Rotates with Earth Axes attached to Earth's crust. Good for lat/lon conversion and find position on Earth
    private Double cartesianItrfX;

    private Double cartesianItrfY;

    private Double cartesianItrfZ;
}
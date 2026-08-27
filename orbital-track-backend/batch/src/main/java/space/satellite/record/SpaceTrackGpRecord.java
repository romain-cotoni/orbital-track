package space.satellite.record;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * Deserialization DTO for a single record from the Space-Track GP (General Perturbation) JSON endpoint.
 * <p>
 * Field names match Space-Track's JSON property names exactly via {@link JsonProperty}.
 * NORAD_CAT_ID comes as a quoted String from the API and is parsed to Integer downstream.
 * EPOCH is kept as a String because Space-Track uses microsecond precision
 * ("yyyy-MM-dd'T'HH:mm:ss.SSSSSS") which requires a custom formatter.
 * </p>
 *
 * @see <a href="https://www.space-track.org/documentation#/gp">Space-Track GP class documentation</a>
 */
public record SpaceTrackGpRecord(

        @JsonProperty("NORAD_CAT_ID")
        String noradCatId,

        @JsonProperty("OBJECT_NAME")
        String objectName,

        @JsonProperty("OBJECT_TYPE")
        String objectType,

        @JsonProperty("COUNTRY_CODE")
        String countryCode,

        @JsonProperty("LAUNCH_DATE")
        LocalDate launchDate,

        @JsonProperty("DECAY_DATE")
        LocalDate decayDate,

        @JsonProperty("RCS_SIZE")
        String rcsSize,

        /** ISO-like UTC datetime with microseconds: "yyyy-MM-dd'T'HH:mm:ss.SSSSSS" */
        @JsonProperty("EPOCH")
        String epoch,

        /** Revolutions per day — used to derive LEO. */
        @JsonProperty("MEAN_MOTION")
        Double meanMotion,

        /** Orbital period in minutes — used to derive MEO. */
        @JsonProperty("PERIOD")
        Double period,

        /** Orbital eccentricity — used to derive HEO. */
        @JsonProperty("ECCENTRICITY")
        Double eccentricity,

        @JsonProperty("TLE_LINE1")
        String tleLine1,

        @JsonProperty("TLE_LINE2")
        String tleLine2
) {}

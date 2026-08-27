package space.satellite.record;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Deserialization DTO for a single record from the CelesTrak GP (General Perturbation) JSON endpoint.
 * <p>
 * CelesTrak returns {@code NORAD_CAT_ID} as a bare integer (not a quoted string).
 * Metadata fields available in Space-Track ({@code COUNTRY_CODE}, {@code LAUNCH_DATE},
 * {@code DECAY_DATE}, {@code RCS_SIZE}) are not provided by CelesTrak and are absent here.
 * {@code PERIOD} is also not returned by CelesTrak, so MEO cannot be derived from period;
 * only LEO (mean motion) and HEO (eccentricity) can be identified.
 * </p>
 *
 * @see <a href="https://celestrak.org/NORAD/documentation/gp-data-formats.php">CelesTrak GP documentation</a>
 */
public record CelestrakGpRecord(

        @JsonProperty("NORAD_CAT_ID")
        Integer noradCatId,

        @JsonProperty("OBJECT_NAME")
        String objectName,

        @JsonProperty("OBJECT_TYPE")
        String objectType,

        /** ISO-like UTC datetime with microseconds: "yyyy-MM-dd'T'HH:mm:ss.SSSSSS" */
        @JsonProperty("EPOCH")
        String epoch,

        /** Revolutions per day — used to derive LEO. */
        @JsonProperty("MEAN_MOTION")
        Double meanMotion,

        /** Orbital eccentricity — used to derive HEO. */
        @JsonProperty("ECCENTRICITY")
        Double eccentricity,

        @JsonProperty("TLE_LINE1")
        String tleLine1,

        @JsonProperty("TLE_LINE2")
        String tleLine2

) {}

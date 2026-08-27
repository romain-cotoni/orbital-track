package space.satellite.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA entity representing a tracked space object (satellite, debris, rocket body).
 * <p>
 * Stores both stable metadata (identity, launch info, physical properties) and
 * the latest TLE (Two-Line Element) orbital data in a single table.
 * Keyed on NORAD Catalog ID — one row per object, updated on each batch run.
 * </p>
 */
@Entity
@Table(name = "satellite")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Satellite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NORAD Catalog ID — unique identifier assigned by US Space Command. */
    @Column(nullable = false, unique = true)
    private Integer noradCatId;

    /** Common name (e.g., "ISS (ZARYA)", "STARLINK-1234"). */
    private String name;

    /** Object classification: PAYLOAD, ROCKET BODY, DEBRIS, TBA. */
    private String objectType;

    /** Two-letter country or organization code (e.g., "US", "PRC", "ISS"). */
    private String countryCode;

    /** Date the object was launched into orbit. */
    private LocalDate launchDate;

    /** Date the object re-entered / decayed. Null means still active in orbit. */
    private LocalDate decayDate;

    /** Radar cross-section size estimate: SMALL, MEDIUM, LARGE. */
    private String rcsSize;

    /** Orbit regime derived from the batch query filter: LEO, MEO, HEO. */
    private String orbitRegime;

    // --- Latest TLE data ---

    /** TLE epoch — reference time for the orbital elements. */
    private Instant epoch;

    /** TLE line 1: satellite number, classification, epoch, drag terms. */
    @Column(length = 69)
    private String line1;

    /** TLE line 2: inclination, RAAN, eccentricity, mean motion. */
    @Column(length = 69)
    private String line2;

    /** Timestamp when this record was last fetched from the data source. */
    private Instant fetchedAt;

    /** Data source: "spacetrack" or "celestrak". */
    private String source;

    /** Constellation the satellite belongs to (e.g. "Starlink", "Galileo", "GLONASS"). Null if none/unknown. */
    private String constellation;

    /** Mission type derived from name (e.g. "INTERNET", "NAVIGATION", "WEATHER", "EARTH_OBS", "COMMUNICATION", "SCIENCE"). Null if unknown. */
    private String missionType;
}

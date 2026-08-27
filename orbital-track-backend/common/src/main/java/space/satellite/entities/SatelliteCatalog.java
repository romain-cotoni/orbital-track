package space.satellite.entities;


import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "satellite_catalog")
@Getter
@Setter
public class SatelliteCatalog {

    @EmbeddedId
    private SatelliteCatalogId id;

    @ManyToOne
    @MapsId("satelliteId")
    @JoinColumn(name = "satellite_id")
    private Satellite satellite;

    @ManyToOne
    @MapsId("catalogId")
    @JoinColumn(name = "catalog_id")
    private Catalog catalog;

    @Column(name = "catalog_identifier", nullable = false)
    private String catalogIdentifier;

    @Column(name = "catalog_name")
    private String catalogName;

}

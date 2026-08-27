package space.satellite.caches;

public record SatelliteKey(String catalogType, String identifier) {

    // Factory method for NORAD catalog
    public static SatelliteKey norad(int number) {
        return new SatelliteKey("NORAD", String.valueOf(number));
    }


}

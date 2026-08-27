package space.satellite.websocket;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.List;
import space.satellite.dtos.SatellitePosition;

/**
 * Encodes a list of satellite positions into a compact binary frame compatible
 * with JavaScript's {@code Float64Array}.
 *
 * <p>Frame layout (all values are 64-bit doubles, little-endian):
 * <pre>
 *   [0]       timestamp_ms   — epoch milliseconds
 *   [1]       count          — number of satellites
 *   [2+i*7]   noradId
 *   [3+i*7]   latDeg
 *   [4+i*7]   lonDeg
 *   [5+i*7]   altM
 *   [6+i*7]   vxMs
 *   [7+i*7]   vyMs
 *   [8+i*7]   vzMs
 * </pre>
 *
 * <p>Total size: {@code (2 + N × 7) × 8} bytes.
 *
 * <p>JS parsing example:
 * <pre>
 *   const v = new Float64Array(buffer);
 *   const count = v[1];
 *   for (let i = 0; i < count; i++) {
 *     const o = 2 + i * 7;
 *     const { noradId, lat, lon, alt } = { noradId: v[o], lat: v[o+1], lon: v[o+2], alt: v[o+3] };
 *   }
 * </pre>
 */
public final class PositionBinaryEncoder {

    private static final int HEADER_DOUBLES   = 2;
    private static final int FIELDS_PER_SAT   = 7;

    private PositionBinaryEncoder() {}

    public static byte[] encode(List<SatellitePosition> positions, Instant timestamp) {
        int n = positions.size();
        ByteBuffer buffer = ByteBuffer
                .allocate((HEADER_DOUBLES + n * FIELDS_PER_SAT) * Double.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);

        buffer.putDouble((double) timestamp.toEpochMilli());
        buffer.putDouble((double) n);

        for (SatellitePosition p : positions) {
            buffer.putDouble(p.noradId());
            buffer.putDouble(p.latDeg());
            buffer.putDouble(p.lonDeg());
            buffer.putDouble(p.altM());
            buffer.putDouble(p.vxMs());
            buffer.putDouble(p.vyMs());
            buffer.putDouble(p.vzMs());
        }

        return buffer.array();
    }
}

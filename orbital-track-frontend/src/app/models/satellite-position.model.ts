export interface SatellitePosition {
  noradId: number;
  lat: number;
  lon: number;
  alt: number;
  vx: number;
  vy: number;
  vz: number;
  timestampMs: number;
}

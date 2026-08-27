export interface RestPosition {
  latDeg: number;
  lonDeg: number;
  altM:   number;
  vxMs:   number;
  vyMs:   number;
  vzMs:   number;
}

export interface SatelliteDetail {
  noradCatId:  number;
  name:        string;
  objectType:  string;
  orbitRegime: string;
  countryCode: string;
  rcsSize:     string;
  launchDate:  string;
  decayDate:   string | null;
  source:      string;
  position:    RestPosition | null;   // null when propagation fails
}

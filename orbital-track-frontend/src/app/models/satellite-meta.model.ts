export interface SatelliteMeta {
  noradCatId: number;
  name: string;
  objectType: string;        // 'PAYLOAD' | 'DEBRIS' | 'ROCKET BODY' | 'UNKNOWN'
  orbitRegime: string;       // 'LEO' | 'MEO' | 'GEO' | 'HEO'
  countryCode: string;
  rcsSize: string;
  launchDate: string;
  decayDate: string | null;
  constellation: string | null;  // e.g. 'Starlink', 'Galileo', 'GLONASS'
  missionType: string | null;    // e.g. 'INTERNET', 'NAVIGATION', 'WEATHER', 'EARTH_OBS', 'COMMUNICATION', 'SCIENCE'
}

export interface SatelliteMetaPage {
  content: SatelliteMeta[];
  totalElements: number;
  totalPages: number;
  number: number;       // current page (0-based)
  size: number;
}

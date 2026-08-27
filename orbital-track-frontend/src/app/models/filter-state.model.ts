export interface FilterState {
  objectTypes: Set<string>;
  orbitRegimes: Set<string>;
  countryCodes: Set<string>;
}

export const DEFAULT_FILTER: FilterState = {
  objectTypes: new Set(),
  orbitRegimes: new Set(),
  countryCodes: new Set(),
};

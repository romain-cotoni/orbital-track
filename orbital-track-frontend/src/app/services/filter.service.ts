import { Injectable, signal } from '@angular/core';
import { SatelliteMeta } from '../models/satellite-meta.model';

@Injectable({ providedIn: 'root' })
export class FilterService {

  // Metadata store: noradId → SatelliteMeta (populated at startup)
  private readonly metaMap = new Map<number, SatelliteMeta>();

  // Active filter sets (empty = show all)
  readonly objectTypes    = signal<Set<string>>(new Set());
  readonly orbitRegimes   = signal<Set<string>>(new Set());
  readonly countryCodes   = signal<Set<string>>(new Set());
  readonly constellations = signal<Set<string>>(new Set());
  readonly missionTypes   = signal<Set<string>>(new Set());

  // Derived sets of all known values (for building filter UI options)
  private readonly _allObjectTypes    = signal<Set<string>>(new Set());
  private readonly _allOrbitRegimes   = signal<Set<string>>(new Set());
  private readonly _allCountryCodes   = signal<Set<string>>(new Set());
  private readonly _allConstellations = signal<Set<string>>(new Set());
  private readonly _allMissionTypes   = signal<Set<string>>(new Set());

  readonly allObjectTypes    = this._allObjectTypes.asReadonly();
  readonly allOrbitRegimes   = this._allOrbitRegimes.asReadonly();
  readonly allCountryCodes   = this._allCountryCodes.asReadonly();
  readonly allConstellations = this._allConstellations.asReadonly();
  readonly allMissionTypes   = this._allMissionTypes.asReadonly();

  registerMeta(meta: SatelliteMeta): void {
    this.metaMap.set(meta.noradCatId, meta);
    if (meta.objectType)    this._allObjectTypes.update(s    => new Set([...s, meta.objectType]));
    if (meta.orbitRegime)   this._allOrbitRegimes.update(s   => new Set([...s, meta.orbitRegime]));
    if (meta.countryCode)   this._allCountryCodes.update(s   => new Set([...s, meta.countryCode]));
    if (meta.constellation) this._allConstellations.update(s => new Set([...s, meta.constellation!]));
    if (meta.missionType)   this._allMissionTypes.update(s   => new Set([...s, meta.missionType!]));
  }

  getMeta(noradId: number): SatelliteMeta | undefined {
    return this.metaMap.get(noradId);
  }

  isVisible(noradId: number): boolean {
    const meta = this.metaMap.get(noradId);
    if (!meta) return true; // not yet loaded → show by default

    const ot = this.objectTypes();
    const or = this.orbitRegimes();
    const cc = this.countryCodes();
    const cn = this.constellations();
    const mt = this.missionTypes();

    if (ot.size > 0 && !ot.has(meta.objectType))   return false;
    if (or.size > 0 && !or.has(meta.orbitRegime))  return false;
    if (cc.size > 0 && !cc.has(meta.countryCode))  return false;
    if (cn.size > 0 && (!meta.constellation || !cn.has(meta.constellation))) return false;
    if (mt.size > 0 && (!meta.missionType   || !mt.has(meta.missionType)))   return false;
    return true;
  }

  toggleObjectType(value: string):    void { this.objectTypes.update(s    => toggle(s, value)); }
  toggleOrbitRegime(value: string):   void { this.orbitRegimes.update(s   => toggle(s, value)); }
  toggleCountryCode(value: string):   void { this.countryCodes.update(s   => toggle(s, value)); }
  toggleConstellation(value: string): void { this.constellations.update(s => toggle(s, value)); }
  toggleMissionType(value: string):   void { this.missionTypes.update(s   => toggle(s, value)); }

  clearAll(): void {
    this.objectTypes.set(new Set());
    this.orbitRegimes.set(new Set());
    this.countryCodes.set(new Set());
    this.constellations.set(new Set());
    this.missionTypes.set(new Set());
  }
}

function toggle(set: Set<string>, value: string): Set<string> {
  const next = new Set(set);
  next.has(value) ? next.delete(value) : next.add(value);
  return next;
}

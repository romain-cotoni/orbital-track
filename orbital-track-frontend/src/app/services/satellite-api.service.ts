import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SatelliteDetail } from '../models/satellite-detail.model';
import { SatelliteMeta, SatelliteMetaPage } from '../models/satellite-meta.model';
import { GroundTrackPoint } from '../models/ground-track-point.model';
import { environment } from '../../environments/environment';

const API = environment.apiUrl;

@Injectable({ providedIn: 'root' })
export class SatelliteApiService {

  constructor(private http: HttpClient) {}

  getMetadataPage(page: number, size: number): Observable<SatelliteMetaPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<SatelliteMetaPage>(`${API}/satellites`, { params });
  }

  searchSatellites(query: { noradCatId?: number; name?: string; countryCode?: string }, size = 10): Observable<SatelliteMetaPage> {
    let params = new HttpParams().set('size', size).set('page', 0);
    if (query.noradCatId !== undefined) params = params.set('noradCatId', query.noradCatId);
    if (query.name)        params = params.set('name', query.name);
    if (query.countryCode) params = params.set('countryCode', query.countryCode);
    return this.http.get<SatelliteMetaPage>(`${API}/satellites`, { params });
  }

  getDetail(noradId: number): Observable<SatelliteDetail> {
    return this.http.get<SatelliteDetail>(`${API}/satellites/${noradId}`);
  }

  getGroundTrack(noradId: number, duration = '90m'): Observable<GroundTrackPoint[]> {
    const params = new HttpParams().set('duration', duration);
    return this.http.get<GroundTrackPoint[]>(`${API}/satellites/${noradId}/ground-track`, { params });
  }
}

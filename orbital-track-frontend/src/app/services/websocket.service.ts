import { Injectable, OnDestroy } from '@angular/core';
import { RxStomp } from '@stomp/rx-stomp';
import { Observable, Subject } from 'rxjs';
import { map, share } from 'rxjs/operators';
import { SatellitePosition } from '../models/satellite-position.model';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {

  private readonly stomp = new RxStomp();

  // Merged stream of all positions from all regime topics
  readonly positions$: Observable<SatellitePosition[]>;

  constructor() {
    this.stomp.configure({
      brokerURL: environment.wsUrl,
      reconnectDelay: 5000,
    });
    this.stomp.activate();

    const leo$ = this.subscribeToTopic('/topic/positions/leo');
    const meo$ = this.subscribeToTopic('/topic/positions/meo');
    const geo$ = this.subscribeToTopic('/topic/positions/geo');
    const heo$ = this.subscribeToTopic('/topic/positions/heo');

    // Merge all regime streams into one
    this.positions$ = new Observable<SatellitePosition[]>(observer => {
      const subs = [
        leo$.subscribe(p => observer.next(p)),
        meo$.subscribe(p => observer.next(p)),
        geo$.subscribe(p => observer.next(p)),
        heo$.subscribe(p => observer.next(p)),
      ];
      return () => subs.forEach(s => s.unsubscribe());
    }).pipe(share());
  }

  /**
   * Subscribes to the per-satellite topic for a given NORAD ID.
   * Returns a single-element array each tick (count=1 frame).
   * Caller is responsible for unsubscribing.
   */
  subscribeToSatellite(noradId: number): Observable<SatellitePosition> {
    return this.subscribeToTopic(`/topic/positions/${noradId}`).pipe(
      map(positions => positions[0]),
    );
  }

  ngOnDestroy(): void {
    this.stomp.deactivate();
  }

  // ---------------------------------------------------------------------------

  private subscribeToTopic(destination: string): Observable<SatellitePosition[]> {
    return this.stomp.watch(destination).pipe(
      map(message => this.decode(message.binaryBody)),
    );
  }

  /**
   * Decodes a binary frame into an array of SatellitePosition.
   *
   * Frame layout (Float64, little-endian):
   *   [0]       timestamp_ms
   *   [1]       count
   *   [2+i*7]   noradId
   *   [3+i*7]   lat
   *   [4+i*7]   lon
   *   [5+i*7]   alt
   *   [6+i*7]   vx
   *   [7+i*7]   vy
   *   [8+i*7]   vz
   */
  private decode(body: Uint8Array): SatellitePosition[] {
    // Build a DataView over a copy to ensure alignment (Uint8Array may be unaligned)
    const buffer = body.buffer.slice(body.byteOffset, body.byteOffset + body.byteLength);
    const view = new DataView(buffer);
    const readF64 = (byteOffset: number) => view.getFloat64(byteOffset, true /* little-endian */);

    const timestampMs = readF64(0);
    const count = readF64(8);
    const positions: SatellitePosition[] = [];

    for (let i = 0; i < count; i++) {
      const base = (2 + i * 7) * 8;
      positions.push({
        noradId:     readF64(base),
        lat:         readF64(base + 8),
        lon:         readF64(base + 16),
        alt:         readF64(base + 24),
        vx:          readF64(base + 32),
        vy:          readF64(base + 40),
        vz:          readF64(base + 48),
        timestampMs,
      });
    }

    return positions;
  }
}

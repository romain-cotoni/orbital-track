import { Component, Input, Output, EventEmitter, OnInit, OnDestroy, NgZone, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { SatelliteDetail } from '../../models/satellite-detail.model';
import { SatellitePosition } from '../../models/satellite-position.model';
import { WebSocketService } from '../../services/websocket.service';

@Component({
  selector: 'app-satellite-detail',
  imports: [CommonModule],
  templateUrl: './satellite-detail.component.html',
})
export class SatelliteDetailComponent implements OnInit, OnDestroy {

  @Input({ required: true }) detail!: SatelliteDetail;
  @Output() closed = new EventEmitter<number>();

  livePosition: SatellitePosition | null = null;

  private sub?: Subscription;

  constructor(
    private ws: WebSocketService,
    private zone: NgZone,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.sub = this.ws.subscribeToSatellite(this.detail.noradCatId).subscribe(pos => {
      // WebSocket arrives outside Angular's zone — zone.run() triggers CD
      this.zone.run(() => {
        this.livePosition = pos;
        this.cdr.detectChanges();
      });
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  get lat(): number { return this.livePosition?.lat ?? this.detail.position?.latDeg ?? 0; }
  get lon(): number { return this.livePosition?.lon ?? this.detail.position?.lonDeg ?? 0; }
  get alt(): number { return this.livePosition?.alt ?? this.detail.position?.altM   ?? 0; }

  get speedKms(): number {
    const vx = this.livePosition?.vx ?? this.detail.position?.vxMs ?? 0;
    const vy = this.livePosition?.vy ?? this.detail.position?.vyMs ?? 0;
    const vz = this.livePosition?.vz ?? this.detail.position?.vzMs ?? 0;
    return Math.sqrt(vx * vx + vy * vy + vz * vz) / 1000;
  }
}

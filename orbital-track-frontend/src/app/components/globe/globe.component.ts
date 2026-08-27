import {
  Component,
  OnDestroy,
  AfterViewInit,
  signal,
  inject,
  effect,
  afterNextRender,
  DestroyRef,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import * as Cesium from 'cesium';

import { WebSocketService } from '../../services/websocket.service';
import { SatelliteApiService } from '../../services/satellite-api.service';
import { FilterService } from '../../services/filter.service';
import { SatellitePosition } from '../../models/satellite-position.model';
import { SatelliteDetail } from '../../models/satellite-detail.model';
import { FilterPanelComponent } from '../filter-panel/filter-panel.component';
import { SatelliteDetailComponent } from '../satellite-detail/satellite-detail.component';
import { SearchBoxComponent } from '../search-box/search-box.component';
import { environment } from '../../../environments/environment';

// ---------------------------------------------------------------------------
// Dead-reckoning state per satellite
// ---------------------------------------------------------------------------
interface DrState {
  position: Cesium.Cartesian3;   // ECEF at lastUpdateMs
  velocity: Cesium.Cartesian3;   // ECEF velocity (m/s)
  lastUpdateMs: number;
  primitive: Cesium.PointPrimitive;
}

// ---------------------------------------------------------------------------
// Color mapping
// ---------------------------------------------------------------------------
function colorForObjectType(objectType: string | undefined): Cesium.Color {
  switch (objectType?.toUpperCase()) {
    case 'PAYLOAD':     return Cesium.Color.WHITE;
    case 'DEBRIS':      return Cesium.Color.RED;
    case 'ROCKET BODY': return Cesium.Color.YELLOW;
    default:            return Cesium.Color.fromCssColorString('#888888');
  }
}

const MAX_SELECTION = 25;

@Component({
  selector: 'app-globe',
  imports: [CommonModule, FilterPanelComponent, SatelliteDetailComponent, SearchBoxComponent],
  templateUrl: './globe.component.html',
  styleUrl: './globe.component.css',
})
export class GlobeComponent implements AfterViewInit, OnDestroy {

  // ---- Injected services --------------------------------------------------
  private readonly ws     = inject(WebSocketService);
  private readonly api    = inject(SatelliteApiService);
  readonly         filter = inject(FilterService);
  private readonly destroyRef = inject(DestroyRef);

  // ---- UI state -----------------------------------------------------------
  readonly filterOpen       = signal(false);
  readonly selectedDetails  = signal<SatelliteDetail[]>([]);
  readonly loadingDetails   = signal<Set<number>>(new Set());
  readonly satelliteCount   = signal(0);
  readonly visibleCount     = signal(0);
  readonly metadataProgress = signal(0); // 0–100

  // ---- Cesium internals ---------------------------------------------------
  private viewer!: Cesium.Viewer;
  private points!: Cesium.PointPrimitiveCollection;
  private selectionBillboards!: Cesium.BillboardCollection;
  private groundTrackCollection!: Cesium.PolylineCollection;
  private postRenderRemover?: () => void;

  // noradId → dead-reckoning state
  private readonly drMap = new Map<number, DrState>();

  // noradId → selection ring billboard
  private readonly selectionBillboardMap = new Map<number, Cesium.Billboard>();

  // noradId → ground track polyline
  private readonly groundTrackMap = new Map<number, Cesium.Polyline>();

  // set of currently selected NORAD IDs
  private readonly selectedNoradIds = new Set<number>();

  // =========================================================================
  // Lifecycle
  // =========================================================================

  constructor() {
    // effect() must live in an injection context — constructor is the safe place
    effect(() => {
      this.filter.objectTypes();
      this.filter.orbitRegimes();
      this.filter.countryCodes();
      this.filter.constellations();
      this.filter.missionTypes();
      this.refreshVisibility();
    });

    // afterNextRender fires once after the first render completes — safe place
    // to start async work that updates signals without triggering NG0100
    afterNextRender(() => this.loadMetadataBackground());
  }

  async ngAfterViewInit(): Promise<void> {
    (window as any).CESIUM_BASE_URL = document.querySelector('base')?.getAttribute('href') + 'assets/cesium/';
    Cesium.Ion.defaultAccessToken = environment.cesiumToken;
    this.viewer = new Cesium.Viewer('cesiumContainer', {
      terrainProvider: await Cesium.createWorldTerrainAsync(),
      animation: false,
      timeline: false,
      baseLayerPicker: false,
      fullscreenButton: false,
      homeButton: false,
      sceneModePicker: false,
      navigationHelpButton: false,
      geocoder: false,
      infoBox: false,
      selectionIndicator: false,
    });

    // PointPrimitiveCollection — far more efficient than Entity for 40k objects
    this.points = this.viewer.scene.primitives.add(new Cesium.PointPrimitiveCollection());

    // BillboardCollection for selection ring indicators
    this.selectionBillboards = this.viewer.scene.primitives.add(new Cesium.BillboardCollection());

    // PolylineCollection for ground tracks
    this.groundTrackCollection = this.viewer.scene.primitives.add(new Cesium.PolylineCollection());

    // Dead-reckoning animation hook — runs every rendered frame
    const handler = () => this.animateFrame();
    this.viewer.scene.postRender.addEventListener(handler);
    this.postRenderRemover = () => this.viewer.scene.postRender.removeEventListener(handler);

    // Click handler — satellite picker
    const screenHandler = new Cesium.ScreenSpaceEventHandler(this.viewer.scene.canvas);
    screenHandler.setInputAction(
      (click: Cesium.ScreenSpaceEventHandler.PositionedEvent) => this.onGlobeClick(click),
      Cesium.ScreenSpaceEventType.LEFT_CLICK,
    );

    // WebSocket position stream
    this.ws.positions$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(positions => {
      this.onPositionsReceived(positions);
    });
  }

  ngOnDestroy(): void {
    this.postRenderRemover?.();
    this.viewer?.destroy();
  }

  // =========================================================================
  // WebSocket → PointPrimitiveCollection
  // =========================================================================

  private onPositionsReceived(positions: SatellitePosition[]): void {
    for (const pos of positions) {
      if (!isFinite(pos.lat) || !isFinite(pos.lon) || !isFinite(pos.alt)) continue;
      const ecef = Cesium.Cartesian3.fromDegrees(pos.lon, pos.lat, pos.alt);
      const vel  = new Cesium.Cartesian3(pos.vx, pos.vy, pos.vz);
      const meta = this.filter.getMeta(pos.noradId);
      const visible = this.filter.isVisible(pos.noradId);

      const existing = this.drMap.get(pos.noradId);
      if (existing) {
        existing.position    = ecef;
        existing.velocity    = vel;
        existing.lastUpdateMs = pos.timestampMs;
        existing.primitive.show = visible;
      } else {
        const prim = this.points.add({
          position:  ecef,
          pixelSize: 3,
          color:     colorForObjectType(meta?.objectType),
          show:      visible,
        });
        this.drMap.set(pos.noradId, { position: ecef, velocity: vel, lastUpdateMs: pos.timestampMs, primitive: prim });
        this.satelliteCount.set(this.drMap.size);
        if (visible) this.visibleCount.update(v => v + 1);
      }
    }
  }

  // =========================================================================
  // Dead-reckoning animation (60 fps)
  // =========================================================================

  private animateFrame(): void {
    const nowMs = Date.now();
    // Scratch objects — avoid GC pressure in hot path
    const scratch = new Cesium.Cartesian3();

    for (const dr of this.drMap.values()) {
      if (!dr.primitive.show) continue;
      const dt = (nowMs - dr.lastUpdateMs) / 1000; // seconds
      // newPos = lastPos + velocity × dt  (linear extrapolation in ECEF)
      scratch.x = dr.position.x + dr.velocity.x * dt;
      scratch.y = dr.position.y + dr.velocity.y * dt;
      scratch.z = dr.position.z + dr.velocity.z * dt;
      dr.primitive.position = Cesium.Cartesian3.clone(scratch);
    }

    // Keep each selection ring locked to its satellite
    for (const [noradId, billboard] of this.selectionBillboardMap) {
      const dr = this.drMap.get(noradId);
      if (dr) {
        const dt = (nowMs - dr.lastUpdateMs) / 1000;
        scratch.x = dr.position.x + dr.velocity.x * dt;
        scratch.y = dr.position.y + dr.velocity.y * dt;
        scratch.z = dr.position.z + dr.velocity.z * dt;
        billboard.position = Cesium.Cartesian3.clone(scratch);
      }
    }
  }

  // =========================================================================
  // Satellite picker
  // =========================================================================

  private onGlobeClick(click: Cesium.ScreenSpaceEventHandler.PositionedEvent): void {
    const picked = this.viewer.scene.pick(click.position);
    if (!Cesium.defined(picked)) return; // click on empty space — no deselect all

    // Find which noradId corresponds to this primitive
    const primitive = picked.primitive as Cesium.PointPrimitive;
    let noradId: number | undefined;
    for (const [id, dr] of this.drMap) {
      if (dr.primitive === primitive) { noradId = id; break; }
    }
    if (noradId === undefined) return;

    if (this.selectedNoradIds.has(noradId)) {
      this.deselectSatellite(noradId);
    } else if (this.selectedNoradIds.size < MAX_SELECTION) {
      this.selectSatellite(noradId);
    }
  }

  onSearchSelected(noradId: number): void {
    if (!this.selectedNoradIds.has(noradId) && this.selectedNoradIds.size < MAX_SELECTION) {
      this.selectSatellite(noradId);
    }
  }

  private selectSatellite(noradId: number): void {
    this.selectedNoradIds.add(noradId);
    this.loadingDetails.update(s => new Set([...s, noradId]));
    this.placeSelectionRing(noradId);

    this.api.getDetail(noradId).subscribe({
      next: detail => {
        this.loadingDetails.update(s => { const n = new Set(s); n.delete(noradId); return n; });
        this.selectedDetails.update(d => [...d, detail]);
        this.loadGroundTrack(noradId);
      },
      error: () => {
        this.loadingDetails.update(s => { const n = new Set(s); n.delete(noradId); return n; });
        this.selectedNoradIds.delete(noradId);
        this.removeSelectionRing(noradId);
      },
    });
  }

  private deselectSatellite(noradId: number): void {
    this.selectedNoradIds.delete(noradId);
    this.removeSelectionRing(noradId);
    this.removeGroundTrack(noradId);
    this.selectedDetails.update(d => d.filter(det => det.noradCatId !== noradId));
    this.loadingDetails.update(s => { const n = new Set(s); n.delete(noradId); return n; });
  }

  private placeSelectionRing(noradId: number): void {
    const dr = this.drMap.get(noradId);
    if (!dr) return;

    const billboard = this.selectionBillboards.add({
      position: Cesium.Cartesian3.clone(dr.primitive.position as Cesium.Cartesian3),
      image:    this.buildRingTexture(),
      width:    24,
      height:   24,
      alignedAxis: Cesium.Cartesian3.ZERO,
    });
    this.selectionBillboardMap.set(noradId, billboard);
  }

  private removeSelectionRing(noradId: number): void {
    const billboard = this.selectionBillboardMap.get(noradId);
    if (billboard) {
      this.selectionBillboards.remove(billboard);
      this.selectionBillboardMap.delete(noradId);
    }
  }

  private buildRingTexture(): HTMLCanvasElement {
    const size = 64;
    const canvas = document.createElement('canvas');
    canvas.width  = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d')!;
    ctx.beginPath();
    ctx.arc(size / 2, size / 2, size / 2 - 3, 0, Math.PI * 2);
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.9)';
    ctx.lineWidth   = 2.5;
    ctx.stroke();
    return canvas;
  }

  toggleFilter(): void {
    this.filterOpen.update(v => !v);
  }

  onDetailClosed(noradId: number): void {
    this.deselectSatellite(noradId);
  }

  // =========================================================================
  // Ground track
  // =========================================================================

  private loadGroundTrack(noradId: number): void {
    this.api.getGroundTrack(noradId).subscribe(points => {
      this.removeGroundTrack(noradId);
      if (points.length < 2) return;

      const positions = points.map(p =>
        Cesium.Cartesian3.fromDegrees(p.lonDeg, p.latDeg, p.altM),
      );

      const polyline = this.groundTrackCollection.add({
        positions,
        width: 1.5,
        material: Cesium.Material.fromType('Color', {
          color: Cesium.Color.fromCssColorString('#00bfff').withAlpha(0.7),
        }),
      });
      this.groundTrackMap.set(noradId, polyline);
      polyline.show = this.filter.isVisible(noradId);
    });
  }

  private removeGroundTrack(noradId: number): void {
    const polyline = this.groundTrackMap.get(noradId);
    if (polyline) {
      this.groundTrackCollection.remove(polyline);
      this.groundTrackMap.delete(noradId);
    }
  }

  // =========================================================================
  // Filter visibility refresh (called when filter changes)
  // =========================================================================

  refreshVisibility(): void {
    let visible = 0;
    for (const [noradId, dr] of this.drMap) {
      dr.primitive.show = this.filter.isVisible(noradId);
      if (dr.primitive.show) visible++;
    }
    this.visibleCount.set(visible);

    // Sync each selection ring and ground track with its satellite's visibility
    for (const [noradId, billboard] of this.selectionBillboardMap) {
      const dr = this.drMap.get(noradId);
      const selectedVisible = dr ? dr.primitive.show : false;
      billboard.show = selectedVisible;
      const polyline = this.groundTrackMap.get(noradId);
      if (polyline) polyline.show = selectedVisible;
    }
  }

  // =========================================================================
  // Metadata background load
  // =========================================================================

  private loadMetadataBackground(): void {
    const pageSize = 500;
    let page = 0;
    let totalPages = 1;

    const loadNext = () => {
      if (page >= totalPages) return;
      this.api.getMetadataPage(page, pageSize).subscribe({
        next: resp => {
          totalPages = resp.totalPages;
          for (const meta of resp.content) {
            this.filter.registerMeta(meta);
            // Recolor existing point if already rendered
            const dr = this.drMap.get(meta.noradCatId);
            if (dr) dr.primitive.color = colorForObjectType(meta.objectType);
          }
          this.metadataProgress.set(Math.round(((page + 1) / totalPages) * 100));
          page++;
          loadNext();
        },
        error: err => console.error('Metadata load error:', err),
      });
    };

    loadNext();
  }
}

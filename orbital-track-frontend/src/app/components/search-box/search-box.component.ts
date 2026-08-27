import {
  Component,
  output,
  signal,
  inject,
  OnDestroy,
  ElementRef,
  viewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { SatelliteApiService } from '../../services/satellite-api.service';
import { SatelliteMeta } from '../../models/satellite-meta.model';

@Component({
  selector: 'app-search-box',
  imports: [CommonModule, FormsModule],
  templateUrl: './search-box.component.html',
})
export class SearchBoxComponent implements OnDestroy {

  readonly satelliteSelected = output<number>();

  readonly query   = signal('');
  readonly results = signal<SatelliteMeta[]>([]);
  readonly loading = signal(false);
  readonly open    = signal(false);

  private readonly api = inject(SatelliteApiService);
  private readonly input$ = new Subject<string>();
  private readonly sub: Subscription;

  readonly inputRef = viewChild<ElementRef<HTMLInputElement>>('inputEl');

  constructor() {
    this.sub = this.input$.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(q => {
        const trimmed = q.trim();
        if (trimmed.length < 2) {
          this.results.set([]);
          this.loading.set(false);
          this.open.set(false);
          return [];
        }
        this.loading.set(true);
        return this.api.searchSatellites(this.buildQuery(trimmed), 10);
      }),
    ).subscribe({
      next: page => {
        this.results.set(page.content);
        this.loading.set(false);
        this.open.set(page.content.length > 0);
      },
      error: () => {
        this.results.set([]);
        this.loading.set(false);
      },
    });
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  onInput(value: string): void {
    this.query.set(value);
    this.input$.next(value);
  }

  select(sat: SatelliteMeta): void {
    this.query.set(sat.name);
    this.results.set([]);
    this.open.set(false);
    this.satelliteSelected.emit(sat.noradCatId);
  }

  clear(): void {
    this.query.set('');
    this.results.set([]);
    this.open.set(false);
  }

  /** Heuristic: number → noradCatId, 1-3 uppercase letters → countryCode, otherwise name */
  private buildQuery(q: string): { noradCatId?: number; name?: string; countryCode?: string } {
    if (/^\d+$/.test(q))              return { noradCatId: parseInt(q, 10) };
    if (/^[A-Z]{1,3}$/.test(q))      return { countryCode: q };
    return { name: q };
  }
}

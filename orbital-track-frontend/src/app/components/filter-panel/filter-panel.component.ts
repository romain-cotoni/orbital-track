import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FilterService } from '../../services/filter.service';

@Component({
  selector: 'app-filter-panel',
  imports: [CommonModule],
  templateUrl: './filter-panel.component.html',
})
export class FilterPanelComponent {
  readonly filter = inject(FilterService);

  labelColor(objectType: string): string {
    switch (objectType?.toUpperCase()) {
      case 'PAYLOAD':     return 'text-white';
      case 'DEBRIS':      return 'text-red-400';
      case 'ROCKET BODY': return 'text-yellow-400';
      default:            return 'text-gray-400';
    }
  }

  missionTypeLabel(mt: string): string {
    switch (mt) {
      case 'INTERNET':      return 'Internet';
      case 'NAVIGATION':    return 'Navigation';
      case 'WEATHER':       return 'Weather';
      case 'EARTH_OBS':     return 'Earth Observation';
      case 'COMMUNICATION': return 'Communication';
      case 'SCIENCE':       return 'Science';
      default:              return mt;
    }
  }
}

import { Component, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ChartConfiguration, ChartData } from 'chart.js';

import { MetricsStateService } from './services/metrics-state.service';
import { AdminStateService } from '../admin/services/admin-state.service';
import { SessionsStateService } from '../sessions/services/sessions-state.service';
import { KpiCardComponent } from '../../shared/components/kpi-card/kpi-card.component';
import { ChartCardComponent } from '../../shared/components/chart-card/chart-card.component';
import { NaCardComponent } from '../../shared/components/na-card/na-card.component';

@Component({
  selector: 'app-metrics',
  standalone: true,
  imports: [CommonModule, KpiCardComponent, ChartCardComponent, NaCardComponent],
  templateUrl: './metrics.component.html',
  styles: [`
    .finops-section-title {
      font-size: 13px; font-weight: 600; text-transform: uppercase;
      letter-spacing: 0.08em; color: rgba(0,0,0,0.45); margin: 28px 0 12px;
    }
    .kpi-section {
      display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 16px; margin-bottom: 24px;
    }
    .charts-row {
      display: grid; grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
      gap: 16px; margin-bottom: 24px;
    }
  `]
})
export class MetricsComponent {
  metricsState = inject(MetricsStateService);
  adminState = inject(AdminStateService);
  sessionsState = inject(SessionsStateService);

  mauActiveNames = computed(() => {
    const ids = this.sessionsState.activeUserIds();
    const map = this.adminState.memberMap();
    const names: string[] = [];
    for (const id of ids) {
      const name = map.get(id);
      if (name) {
        const short = name.includes('@') ? name.split('@')[0] : name;
        names.push(short);
      }
    }
    return names;
  });

  mauSubtitle = computed(() => {
    const names = this.mauActiveNames();
    if (names.length === 0) {
      return this.metricsState.mauLabel();
    }
    return names.join(', ');
  });

  // PRs chart
  prsChartData = computed<ChartData<'bar'>>(() => {
    const metrics = this.metricsState.prsMetrics();
    return {
      labels: metrics.map(m => m.date ?? ''),
      datasets: [{
        data: metrics.map(m => (m.count ?? m.value) ?? 0),
        label: 'Pull Requests', backgroundColor: '#9c27b0', borderColor: '#9c27b0', borderWidth: 1
      }]
    };
  });

  prsChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true, maintainAspectRatio: false,
    scales: {
      x: { title: { display: true, text: 'Date' } },
      y: { title: { display: true, text: 'PRs' }, beginAtZero: true }
    },
    plugins: { legend: { display: true, position: 'top' } }
  };

  // Sessions per day chart — calculated from session created_at timestamps
  sessionsPerDayChartData = computed<ChartData<'bar'>>(() => {
    const perDay = this.sessionsState.sessionsPerDay();
    return {
      labels: perDay.map(d => d.date),
      datasets: [{
        data: perDay.map(d => d.count),
        label: 'Sesiones creadas', backgroundColor: '#3f51b5', borderColor: '#3f51b5', borderWidth: 1
      }]
    };
  });

  sessionsMetricsChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true, maintainAspectRatio: false,
    scales: {
      x: { title: { display: true, text: 'Date' } },
      y: { title: { display: true, text: 'Sessions' }, beginAtZero: true }
    },
    plugins: { legend: { display: true, position: 'top' } }
  };

}

import { Component, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ChartConfiguration, ChartData } from 'chart.js';

import { MetricsStateService } from './services/metrics-state.service';
import { KpiCardComponent } from '../../shared/components/kpi-card/kpi-card.component';
import { ChartCardComponent } from '../../shared/components/chart-card/chart-card.component';

@Component({
  selector: 'app-metrics',
  standalone: true,
  imports: [CommonModule, KpiCardComponent, ChartCardComponent],
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

  // Sessions metrics chart
  sessionsMetricsChartData = computed<ChartData<'bar'>>(() => {
    const metrics = this.metricsState.sessionsMetrics();
    return {
      labels: metrics.map(m => m.date ?? ''),
      datasets: [{
        data: metrics.map(m => (m.count ?? m.value) ?? 0),
        label: 'Sessions', backgroundColor: '#3f51b5', borderColor: '#3f51b5', borderWidth: 1
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

  // Usage chart
  usageChartData = computed<ChartData<'line'>>(() => {
    const metrics = this.metricsState.usageMetrics();
    return {
      labels: metrics.map(m => m.date ?? ''),
      datasets: [{
        data: metrics.map(m => (m.count ?? m.value) ?? 0),
        label: 'Usage', fill: true, tension: 0.4,
        borderColor: '#00bcd4', backgroundColor: 'rgba(0, 188, 212, 0.1)'
      }]
    };
  });

  usageChartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true, maintainAspectRatio: false,
    scales: {
      x: { title: { display: true, text: 'Date' } },
      y: { title: { display: true, text: 'Usage' }, beginAtZero: true }
    },
    plugins: { legend: { display: true, position: 'top' } }
  };

  // Searches chart
  searchesChartData = computed<ChartData<'bar'>>(() => {
    const metrics = this.metricsState.searchesMetrics();
    return {
      labels: metrics.map(m => m.date ?? ''),
      datasets: [{
        data: metrics.map(m => (m.count ?? m.value) ?? 0),
        label: 'Searches', backgroundColor: '#009688', borderColor: '#009688', borderWidth: 1
      }]
    };
  });

  searchesChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true, maintainAspectRatio: false,
    scales: {
      x: { title: { display: true, text: 'Date' } },
      y: { title: { display: true, text: 'Searches' }, beginAtZero: true }
    },
    plugins: { legend: { display: true, position: 'top' } }
  };
}

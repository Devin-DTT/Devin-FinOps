import { Component, inject, ViewChild, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartData } from 'chart.js';

import { SessionsStateService } from './services/sessions-state.service';
import { BillingStateService } from '../billing/services/billing-state.service';
import { MetricsStateService } from '../metrics/services/metrics-state.service';
import { DevinSession, SessionStatus } from './models/session.model';
import { KpiCardComponent } from '../../shared/components/kpi-card/kpi-card.component';
import { NaCardComponent } from '../../shared/components/na-card/na-card.component';
import { ChartCardComponent } from '../../shared/components/chart-card/chart-card.component';

@Component({
  selector: 'app-sessions',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatChipsModule,
    MatFormFieldModule,
    MatSelectModule,
    MatSortModule,
    MatProgressBarModule,
    MatTooltipModule,
    BaseChartDirective,
    KpiCardComponent,
    NaCardComponent,
    ChartCardComponent
  ],
  templateUrl: './sessions.component.html',
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
    .wide-card { grid-column: span 2; }
    .kpi-card { text-align: center; }
    .kpi-value { font-size: 42px; font-weight: 700; display: block; margin-top: 8px; }
    .kpi-sub { font-size: 13px; color: rgba(0,0,0,0.54); display: block; margin-top: 4px; }
    .kpi-acu .kpi-value { color: #ff9800; }
    .acu-bar { margin-top: 12px; border-radius: 4px; }
    .table-card { margin-bottom: 24px; }
    .table-card mat-card-header { display: flex; align-items: center; margin-bottom: 16px; }
    .status-filter { width: 200px; margin-left: 16px; }
    .sessions-table { width: 100%; }
    .id-cell { font-family: monospace; font-size: 12px; max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .no-data-row td { text-align: center; padding: 24px; color: rgba(0,0,0,0.54); font-style: italic; }
  `]
})
export class SessionsComponent implements OnInit {
  @ViewChild(MatSort) sort!: MatSort;

  sessionsState = inject(SessionsStateService);
  billingState = inject(BillingStateService);
  metricsState = inject(MetricsStateService);

  displayedColumns: string[] = ['session_id', 'title', 'status', 'origin', 'created_at'];
  dataSource = new MatTableDataSource<DevinSession>([]);
  selectedStatusFilter: SessionStatus | 'all' = 'all';
  statusOptions: Array<SessionStatus | 'all'> = ['all', 'running', 'finished', 'failed', 'stopped', 'suspended', 'blocked'];

  get acuPerPr(): number {
    const totalPrs = this.metricsState.prsMetrics().reduce((acc, m) => acc + ((m.count ?? m.value) ?? 0), 0);
    return totalPrs > 0 ? this.billingState.currentCycleAcu() / totalPrs : 0;
  }

  get prsPerAcu(): number {
    const totalPrs = this.metricsState.prsMetrics().reduce((acc, m) => acc + ((m.count ?? m.value) ?? 0), 0);
    return this.billingState.currentCycleAcu() > 0 ? totalPrs / this.billingState.currentCycleAcu() : 0;
  }

  get acuWasted(): number {
    const total = this.sessionsState.totalSessions();
    return total > 0
      ? ((this.sessionsState.failedSessions() + this.sessionsState.stoppedSessions()) / total) * this.billingState.currentCycleAcu()
      : 0;
  }

  // Session donut chart
  get sessionDonutData(): ChartData<'doughnut'> {
    return {
      labels: ['Running', 'Finished', 'Failed', 'Stopped'],
      datasets: [{
        data: [
          this.sessionsState.runningSessions(),
          this.sessionsState.finishedSessions(),
          this.sessionsState.failedSessions(),
          this.sessionsState.stoppedSessions()
        ],
        backgroundColor: ['#3f51b5', '#4caf50', '#f44336', '#9e9e9e'],
        hoverBackgroundColor: ['#5c6bc0', '#66bb6a', '#ef5350', '#bdbdbd']
      }]
    };
  }

  sessionDonutOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true, maintainAspectRatio: false,
    plugins: { legend: { display: true, position: 'right' } }
  };

  // Sessions metrics chart
  get sessionsMetricsChartData(): ChartData<'bar'> {
    const metrics = this.metricsState.sessionsMetrics();
    return {
      labels: metrics.map(m => m.date ?? ''),
      datasets: [{
        data: metrics.map(m => (m.count ?? m.value) ?? 0),
        label: 'Sessions', backgroundColor: '#3f51b5', borderColor: '#3f51b5', borderWidth: 1
      }]
    };
  }

  sessionsMetricsChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true, maintainAspectRatio: false,
    scales: {
      x: { title: { display: true, text: 'Date' } },
      y: { title: { display: true, text: 'Sessions' }, beginAtZero: true }
    },
    plugins: { legend: { display: true, position: 'top' } }
  };

  ngOnInit(): void {
    // Update table data when sessions signal changes
    // Using effect would be ideal but for simplicity we poll via getter
  }

  get tableData(): DevinSession[] {
    const sessions = this.sessionsState.sessions();
    this.dataSource.data = sessions;
    if (this.sort) {
      this.dataSource.sort = this.sort;
    }
    this.applyFilter();
    return sessions;
  }

  onStatusFilterChange(): void {
    this.applyFilter();
  }

  getStatusColor(status: SessionStatus): string {
    const colorMap: Record<string, string> = {
      running: 'primary', finished: 'accent', failed: 'warn',
      stopped: '', suspended: '', blocked: 'warn', unknown: ''
    };
    return colorMap[status] || '';
  }

  formatTimestamp(isoString: string): string {
    if (!isoString) return '-';
    return new Date(isoString).toLocaleString();
  }

  private applyFilter(): void {
    if (this.selectedStatusFilter === 'all') {
      this.dataSource.filter = '';
    } else {
      this.dataSource.filterPredicate = (session: DevinSession, filter: string) => session.status === filter;
      this.dataSource.filter = this.selectedStatusFilter;
    }
  }
}

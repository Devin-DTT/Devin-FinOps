import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartData } from 'chart.js';

import { BillingStateService } from './services/billing-state.service';
import { AdminStateService } from '../admin/services/admin-state.service';
import { SessionsStateService } from '../sessions/services/sessions-state.service';
import { KpiCardComponent } from '../../shared/components/kpi-card/kpi-card.component';
import { NaCardComponent } from '../../shared/components/na-card/na-card.component';
import { ChartCardComponent } from '../../shared/components/chart-card/chart-card.component';

@Component({
  selector: 'app-billing',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatProgressBarModule,
    BaseChartDirective,
    KpiCardComponent,
    NaCardComponent,
    ChartCardComponent
  ],
  templateUrl: './billing.component.html',
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
  `]
})
export class BillingComponent {
  billingState = inject(BillingStateService);
  adminState = inject(AdminStateService);
  sessionsState = inject(SessionsStateService);

  get acuPerUser(): number {
    return this.adminState.userCount() > 0
      ? this.billingState.currentCycleAcu() / this.adminState.userCount()
      : 0;
  }

  get acuPerSession(): number {
    return this.sessionsState.totalSessions() > 0
      ? this.billingState.currentCycleAcu() / this.sessionsState.totalSessions()
      : 0;
  }

  // Chart: Daily ACU consumption
  get acuChartData(): ChartData<'line'> {
    const entries = [...this.billingState.dailyConsumption()].sort((a, b) => a.date.localeCompare(b.date));
    return {
      labels: entries.map(e => e.date),
      datasets: [{
        data: entries.map(e => e.acu_consumed),
        label: 'ACU Consumed',
        fill: true, tension: 0.4,
        borderColor: '#ff9800',
        backgroundColor: 'rgba(255, 152, 0, 0.1)'
      }]
    };
  }

  acuChartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true, maintainAspectRatio: false,
    scales: {
      x: { title: { display: true, text: 'Date' } },
      y: { title: { display: true, text: 'ACU' }, beginAtZero: true }
    },
    plugins: { legend: { display: true, position: 'top' } }
  };

  // Chart: Billing cycles history
  get billingCyclesChartData(): ChartData<'bar'> {
    const cycles = this.billingState.billingCycles();
    return {
      labels: cycles.map(c => c.start_date),
      datasets: [{
        data: cycles.map(c => c.acu_usage ?? 0),
        label: 'ACU Usage per Cycle',
        backgroundColor: '#ff9800',
        borderColor: '#ff9800',
        borderWidth: 1
      }]
    };
  }

  billingCyclesChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true, maintainAspectRatio: false,
    scales: {
      x: { title: { display: true, text: 'Billing Cycle' } },
      y: { title: { display: true, text: 'ACU Usage' }, beginAtZero: true }
    },
    plugins: { legend: { display: true, position: 'top' } }
  };
}

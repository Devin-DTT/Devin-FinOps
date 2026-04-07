import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';

import { AdminStateService } from './services/admin-state.service';
import { KpiCardComponent } from '../../shared/components/kpi-card/kpi-card.component';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatChipsModule, KpiCardComponent],
  templateUrl: './admin.component.html',
  styles: [`
    .finops-section-title {
      font-size: 13px; font-weight: 600; text-transform: uppercase;
      letter-spacing: 0.08em; color: rgba(0,0,0,0.45); margin: 28px 0 12px;
    }
    .kpi-section {
      display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 16px; margin-bottom: 24px;
    }
    .kpi-card { text-align: center; }
    .kpi-value { font-size: 42px; font-weight: 700; display: block; margin-top: 8px; }
    .kpi-sub { font-size: 13px; color: rgba(0,0,0,0.54); display: block; margin-top: 4px; }
  `]
})
export class AdminComponent {
  adminState = inject(AdminStateService);

  getQueueStatusColor(): string {
    const status = this.adminState.queueStatus().toLowerCase();
    if (status === 'normal') return 'accent';
    if (status === 'elevated') return '';
    if (status === 'high') return 'warn';
    return '';
  }
}

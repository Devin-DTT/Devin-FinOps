import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MetricsResult } from '../../models/metrics.model';
import { MetricsService } from '../../services/metrics.service';

@Component({
  selector: 'app-metrics-display',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './metrics-display.component.html',
  styleUrl: './metrics-display.component.scss'
})
export class MetricsDisplayComponent {
  @Input() metricsResult: MetricsResult | null = null;

  constructor(private metricsService: MetricsService) {}

  get costPerUser(): Record<string, number> {
    return this.metricsService.getCostPerUser(this.metricsResult);
  }

  get averageAcusPerSession(): number {
    return this.metricsService.getAverageAcusPerSession(this.metricsResult);
  }

  get isCostConsistent(): boolean {
    return this.metricsService.validateCostConsistency(this.metricsResult);
  }

  get currency(): string {
    return this.metricsResult?.config?.currency ?? 'USD';
  }

  get reportingPeriod(): string {
    if (!this.metricsResult?.reporting_period) {
      return 'N/A';
    }
    const rp = this.metricsResult.reporting_period;
    return `${rp.start_date} - ${rp.end_date}`;
  }

  formatCost(value: number): string {
    return this.metricsService.formatCurrency(value, this.currency);
  }

  get costPerUserEntries(): Array<{user: string; cost: number}> {
    const cpu = this.costPerUser;
    return Object.entries(cpu).map(([user, cost]) => ({ user, cost }));
  }
}

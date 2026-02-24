import { Injectable } from '@angular/core';
import { MetricsResult, MetricsData } from '../models/metrics.model';

@Injectable({
  providedIn: 'root'
})
export class MetricsService {

  /**
   * Extracts a specific metric value from the metrics result.
   */
  getMetricValue(metrics: MetricsResult | null, key: keyof MetricsData): number | Record<string, number> | null {
    if (!metrics || !metrics.metrics) {
      return null;
    }
    return metrics.metrics[key] ?? null;
  }

  /**
   * Returns total monthly cost from metrics.
   */
  getTotalCost(metrics: MetricsResult | null): number {
    const value = this.getMetricValue(metrics, '01_total_monthly_cost');
    return typeof value === 'number' ? value : 0;
  }

  /**
   * Returns total ACUs from metrics.
   */
  getTotalAcus(metrics: MetricsResult | null): number {
    const value = this.getMetricValue(metrics, '02_total_acus');
    return typeof value === 'number' ? value : 0;
  }

  /**
   * Returns total sessions count from metrics.
   */
  getTotalSessions(metrics: MetricsResult | null): number {
    const value = this.getMetricValue(metrics, '06_total_sessions');
    return typeof value === 'number' ? value : 0;
  }

  /**
   * Returns unique users count from metrics.
   */
  getUniqueUsers(metrics: MetricsResult | null): number {
    const value = this.getMetricValue(metrics, '12_unique_users');
    return typeof value === 'number' ? value : 0;
  }

  /**
   * Returns cost per user map from metrics.
   */
  getCostPerUser(metrics: MetricsResult | null): Record<string, number> {
    const value = this.getMetricValue(metrics, '03_cost_per_user');
    return (typeof value === 'object' && value !== null) ? value : {};
  }

  /**
   * Returns average ACUs per session.
   */
  getAverageAcusPerSession(metrics: MetricsResult | null): number {
    const value = this.getMetricValue(metrics, '05_average_acus_per_session');
    return typeof value === 'number' ? value : 0;
  }

  /**
   * Validates consistency: cost should equal acus * price_per_acu.
   */
  validateCostConsistency(metrics: MetricsResult | null): boolean {
    if (!metrics || !metrics.metrics || !metrics.config) {
      return false;
    }
    const totalCost = metrics.metrics['01_total_monthly_cost'];
    const totalAcus = metrics.metrics['02_total_acus'];
    const pricePerAcu = metrics.config.price_per_acu;
    const expectedCost = totalAcus * pricePerAcu;
    return Math.abs(totalCost - expectedCost) < 0.01;
  }

  /**
   * Validates that per-user costs sum to total cost.
   */
  validateCostPerUserSumsToTotal(metrics: MetricsResult | null): boolean {
    if (!metrics || !metrics.metrics) {
      return false;
    }
    const costPerUser = metrics.metrics['03_cost_per_user'];
    const totalCost = metrics.metrics['01_total_monthly_cost'];
    const sumCosts = Object.values(costPerUser).reduce((sum, val) => sum + val, 0);
    return Math.abs(totalCost - sumCosts) < 0.01;
  }

  /**
   * Formats a currency value for display.
   */
  formatCurrency(value: number, currency: string = 'USD'): string {
    return `${value.toFixed(2)} ${currency}`;
  }

  /**
   * Formats a duration in minutes to a human-readable string.
   */
  formatDuration(minutes: number): string {
    if (minutes < 60) {
      return `${minutes.toFixed(0)} min`;
    }
    const hours = Math.floor(minutes / 60);
    const remainingMinutes = Math.round(minutes % 60);
    return `${hours}h ${remainingMinutes}m`;
  }
}

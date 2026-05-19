import { Injectable, signal } from '@angular/core';
import { MetricDataPoint } from '../models/metrics.model';
import { WebSocketMessage } from '../../../models/devin-data.model';

@Injectable({ providedIn: 'root' })
export class MetricsStateService {
  // Signals
  dauCount = signal(0);
  wauCount = signal(0);
  mauCount = signal(0);
  dauLabel = signal('');
  wauLabel = signal('');
  mauLabel = signal('');
  sessionsMetrics = signal<MetricDataPoint[]>([]);
  prsMetrics = signal<MetricDataPoint[]>([]);
  usageMetrics = signal<MetricDataPoint[]>([]);
  searchesMetrics = signal<MetricDataPoint[]>([]);
  activeUsersMetrics = signal<MetricDataPoint[]>([]);
  lastUpdated = signal(0);

  // Summary totals (from endpoints that return aggregates instead of time series)
  prsCreatedTotal = signal(0);
  prsMergedTotal = signal(0);
  sessionsCreatedTotal = signal(0);
  searchesCreatedTotal = signal(0);

  handleMessage(msg: WebSocketMessage): void {
    const data = msg.data as Record<string, unknown>;
    this.lastUpdated.set(msg.timestamp);

    switch (msg.endpoint) {
      case 'get_dau_metrics': {
        const dauResult = this.extractMetricWithPeriod(data, 'day');
        this.dauCount.set(dauResult.value);
        this.dauLabel.set(dauResult.label);
        break;
      }
      case 'get_wau_metrics': {
        const wauResult = this.extractMetricWithPeriod(data, 'week');
        this.wauCount.set(wauResult.value);
        this.wauLabel.set(wauResult.label);
        break;
      }
      case 'get_mau_metrics': {
        const mauResult = this.extractMetricWithPeriod(data, 'month');
        this.mauCount.set(mauResult.value);
        this.mauLabel.set(mauResult.label);
        break;
      }
      case 'get_sessions_metrics':
        this.sessionsMetrics.set(this.normalizeMetricTimeSeries(data, 'sessions'));
        this.extractSessionsSummary(data);
        break;
      case 'get_prs_metrics':
        this.prsMetrics.set(this.normalizeMetricTimeSeries(data, 'prs'));
        this.extractPrsSummary(data);
        break;
      case 'get_usage_metrics':
        this.usageMetrics.set(this.normalizeMetricTimeSeries(data, 'usage'));
        break;
      case 'get_searches_metrics':
        this.searchesMetrics.set(this.normalizeMetricTimeSeries(data, 'searches'));
        if (typeof data['searches_created_count'] === 'number') {
          this.searchesCreatedTotal.set(data['searches_created_count'] as number);
        }
        break;
      case 'get_active_users_metrics':
        this.activeUsersMetrics.set(this.normalizeMetricTimeSeries(data, 'active_users'));
        break;
    }
  }

  private extractMetricCount(data: Record<string, unknown>): number {
    if (typeof data['count'] === 'number') return data['count'] as number;
    if (typeof data['value'] === 'number') return data['value'] as number;
    if (Array.isArray(data)) {
      return this.extractLastMetricFromArray(data as Record<string, unknown>[]);
    }
    const arr = Array.isArray(data['items'])
      ? data['items'] as Record<string, unknown>[]
      : (Array.isArray(data['data']) ? data['data'] as Record<string, unknown>[] : []);
    if (arr.length > 0) {
      return this.extractLastMetricFromArray(arr);
    }
    return 0;
  }

  private extractLastMetricFromArray(entries: Record<string, unknown>[]): number {
    if (entries.length === 0) return 0;
    const result = this.extractMetricFromArrayWithIndex(entries);
    return result.value;
  }

  private extractMetricFromArrayWithIndex(entries: Record<string, unknown>[]): { value: number; index: number } {
    if (entries.length === 0) return { value: 0, index: -1 };
    const last = entries[entries.length - 1];
    const lastVal = (last['active_users'] as number)
      ?? (last['count'] as number)
      ?? (last['value'] as number)
      ?? 0;
    if (lastVal > 0) return { value: lastVal, index: entries.length - 1 };
    for (let i = entries.length - 2; i >= 0; i--) {
      const entry = entries[i];
      const val = (entry['active_users'] as number)
        ?? (entry['count'] as number)
        ?? (entry['value'] as number)
        ?? 0;
      if (val > 0) return { value: val, index: i };
    }
    return { value: 0, index: -1 };
  }

  private extractMetricWithPeriod(
    data: Record<string, unknown>,
    periodType: 'day' | 'week' | 'month'
  ): { value: number; label: string } {
    let entries: Record<string, unknown>[] = [];
    if (Array.isArray(data)) {
      entries = data as Record<string, unknown>[];
    } else if (Array.isArray(data['items'])) {
      entries = data['items'] as Record<string, unknown>[];
    } else if (Array.isArray(data['data'])) {
      entries = data['data'] as Record<string, unknown>[];
    }
    if (entries.length === 0) {
      if (typeof data['count'] === 'number') return { value: data['count'] as number, label: '' };
      if (typeof data['value'] === 'number') return { value: data['value'] as number, label: '' };
      return { value: 0, label: '' };
    }
    const result = this.extractMetricFromArrayWithIndex(entries);
    if (result.index === entries.length - 1 || result.index === -1) {
      return { value: result.value, label: '' };
    }
    const entry = entries[result.index];
    const epochSec = (entry['start_time'] as number) ?? 0;
    if (epochSec > 0) {
      const d = new Date(epochSec * 1000);
      const dateStr = d.toLocaleDateString('es-ES', { day: 'numeric', month: 'short' });
      const periodNames = { day: 'Último dato', week: 'Última semana activa', month: 'Último mes activo' };
      return { value: result.value, label: `${periodNames[periodType]}: ${dateStr}` };
    }
    return { value: result.value, label: 'Dato del último periodo con actividad' };
  }

  normalizeMetricTimeSeries(
    data: Record<string, unknown>,
    countField: string
  ): MetricDataPoint[] {
    let entries: Record<string, unknown>[];
    if (Array.isArray(data)) {
      entries = data as Record<string, unknown>[];
    } else if (Array.isArray(data['items'])) {
      entries = data['items'] as Record<string, unknown>[];
    } else if (Array.isArray(data['data'])) {
      entries = data['data'] as Record<string, unknown>[];
    } else {
      return this.extractArray<MetricDataPoint>(data, 'data');
    }
    return entries.map(e => {
      const epochSec = (e['start_time'] as number) ?? 0;
      const dateStr = epochSec > 0
        ? new Date(epochSec * 1000).toISOString().split('T')[0]
        : (e['date'] as string) ?? '';
      const val = (e[countField] as number)
        ?? (e['active_users'] as number)
        ?? (e['count'] as number)
        ?? (e['value'] as number)
        ?? 0;
      return { date: dateStr, count: val } as MetricDataPoint;
    });
  }

  private extractPrsSummary(data: Record<string, unknown>): void {
    if (typeof data['prs_created_count'] === 'number') {
      this.prsCreatedTotal.set(data['prs_created_count'] as number);
    }
    if (typeof data['prs_merged_count'] === 'number') {
      this.prsMergedTotal.set(data['prs_merged_count'] as number);
    }
  }

  private extractSessionsSummary(data: Record<string, unknown>): void {
    if (typeof data['sessions_created_count'] === 'number') {
      this.sessionsCreatedTotal.set(data['sessions_created_count'] as number);
    }
  }

  private extractArray<T>(data: Record<string, unknown>, key: string): T[] {
    if (Array.isArray(data)) return data as T[];
    const value = data[key];
    if (Array.isArray(value)) return value as T[];
    for (const k of Object.keys(data)) {
      if (Array.isArray(data[k])) return data[k] as T[];
    }
    return [];
  }
}

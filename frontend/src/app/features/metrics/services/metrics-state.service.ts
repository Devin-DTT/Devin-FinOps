import { Injectable, signal } from '@angular/core';
import { MetricDataPoint } from '../models/metrics.model';
import { WebSocketMessage } from '../../../models/devin-data.model';

@Injectable({ providedIn: 'root' })
export class MetricsStateService {
  // Signals
  dauCount = signal(0);
  wauCount = signal(0);
  mauCount = signal(0);
  sessionsMetrics = signal<MetricDataPoint[]>([]);
  prsMetrics = signal<MetricDataPoint[]>([]);
  usageMetrics = signal<MetricDataPoint[]>([]);
  searchesMetrics = signal<MetricDataPoint[]>([]);
  activeUsersMetrics = signal<MetricDataPoint[]>([]);
  lastUpdated = signal(0);

  handleMessage(msg: WebSocketMessage): void {
    const data = msg.data as Record<string, unknown>;
    this.lastUpdated.set(msg.timestamp);

    switch (msg.endpoint) {
      case 'get_dau_metrics':
        this.dauCount.set(this.extractMetricCount(data));
        break;
      case 'get_wau_metrics':
        this.wauCount.set(this.extractMetricCount(data));
        break;
      case 'get_mau_metrics':
        this.mauCount.set(this.extractMetricCount(data));
        break;
      case 'get_sessions_metrics':
        this.sessionsMetrics.set(this.normalizeMetricTimeSeries(data, 'sessions'));
        break;
      case 'get_prs_metrics':
        this.prsMetrics.set(this.normalizeMetricTimeSeries(data, 'prs'));
        break;
      case 'get_usage_metrics':
        this.usageMetrics.set(this.normalizeMetricTimeSeries(data, 'usage'));
        break;
      case 'get_searches_metrics':
        this.searchesMetrics.set(this.normalizeMetricTimeSeries(data, 'searches'));
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
    const last = entries[entries.length - 1];
    return (last['active_users'] as number)
      ?? (last['count'] as number)
      ?? (last['value'] as number)
      ?? 0;
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

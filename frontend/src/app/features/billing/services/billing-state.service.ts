import { Injectable, signal, computed } from '@angular/core';
import { BillingCycle, DailyConsumption } from '../models/billing.model';
import { WebSocketMessage } from '../../../models/devin-data.model';

@Injectable({ providedIn: 'root' })
export class BillingStateService {
  // Signals
  currentCycleAcu = signal(0);
  currentCycleLimit = signal(0);
  billingCycles = signal<BillingCycle[]>([]);
  dailyConsumption = signal<DailyConsumption[]>([]);
  lastUpdated = signal(0);

  // Computed signals
  acuUsagePercent = computed(() =>
    this.currentCycleLimit() > 0
      ? Math.round((this.currentCycleAcu() / this.currentCycleLimit()) * 100)
      : 0
  );

  handleMessage(msg: WebSocketMessage): void {
    const data = msg.data as Record<string, unknown>;
    this.lastUpdated.set(msg.timestamp);

    switch (msg.endpoint) {
      case 'list_billing_cycles':
        this.handleBillingCycles(data);
        break;
      case 'get_daily_consumption':
        this.handleDailyConsumption(data);
        break;
      case 'get_acu_limits':
        this.handleAcuLimits(data);
        break;
    }
  }

  private handleBillingCycles(data: Record<string, unknown>): void {
    const cycles = this.extractArray<BillingCycle>(data, 'cycles');
    this.billingCycles.set(cycles);
    if (cycles.length > 0) {
      const current = cycles[cycles.length - 1];
      this.currentCycleAcu.set(current.acu_usage ?? 0);
      this.currentCycleLimit.set(current.acu_limit ?? 0);
    }
  }

  private handleDailyConsumption(data: Record<string, unknown>): void {
    // Use total_acus as current cycle ACU when billing cycles are unavailable
    const totalAcus = data['total_acus'];
    if (typeof totalAcus === 'number' && totalAcus > 0 && this.currentCycleAcu() === 0) {
      this.currentCycleAcu.set(totalAcus);
    }

    let entries: DailyConsumption[] = [];
    if (Array.isArray(data['daily_consumption'])) {
      entries = data['daily_consumption'] as DailyConsumption[];
    } else if (Array.isArray(data['consumption_by_date'])) {
      const rawEntries = data['consumption_by_date'] as Record<string, unknown>[];
      entries = rawEntries.map(e => ({
        date: typeof e['date'] === 'number'
          ? new Date((e['date'] as number) * 1000).toISOString().split('T')[0]
          : String(e['date'] ?? ''),
        acu_consumed: (e['acus'] as number) ?? 0
      }));
    } else {
      entries = this.extractArray<DailyConsumption>(data, 'daily_consumption');
    }
    this.dailyConsumption.set(entries);
  }

  private handleAcuLimits(data: Record<string, unknown>): void {
    // Direct top-level fields
    const limit = this.extractNumber(data, 'acu_limit') ?? this.extractNumber(data, 'limit');
    if (limit !== null) {
      this.currentCycleLimit.set(limit);
      return;
    }
    // API returns {items: [{cycle_acu_limit: N, scope: "...", org_id: "..."}]}
    const items = data['items'];
    if (Array.isArray(items)) {
      let totalLimit = 0;
      for (const item of items as Record<string, unknown>[]) {
        const cycleLimit = item['cycle_acu_limit'];
        if (typeof cycleLimit === 'number') {
          totalLimit += cycleLimit;
        }
      }
      if (totalLimit > 0) {
        this.currentCycleLimit.set(totalLimit);
      }
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

  private extractNumber(data: Record<string, unknown>, key: string): number | null {
    const val = data[key];
    if (typeof val === 'number') return val;
    return null;
  }
}

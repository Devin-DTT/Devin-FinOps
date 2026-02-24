import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import {
  ConsumptionData,
  DailyConsumption,
  WeeklyConsumption,
  ExecutiveMetrics,
  FilterState
} from '../models/consumption-data.model';

/** Cost per ACU in USD */
const COST_PER_ACU = 3.33;

/** Spanish month names */
const MONTH_NAMES = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'
];

/**
 * FinOps data service that processes consumption data, applies filters,
 * calculates executive metrics, and provides data for charts.
 * Replaces the calculateMetricsFromData() function from the vanilla JS dashboard.
 */
@Injectable({
  providedIn: 'root'
})
export class FinopsService {

  private allDataSubject = new BehaviorSubject<DailyConsumption[]>([]);
  private filteredDataSubject = new BehaviorSubject<DailyConsumption[]>([]);
  private metricsSubject = new BehaviorSubject<ExecutiveMetrics>({
    acusMes: 0, acusMesAnterior: 0,
    costeMes: 0, costeMesAnterior: 0,
    diferenciaACUs: 0, diferenciaCost: 0,
    porcentajeACUs: 0, porcentajeCost: 0,
    mesActualLabel: '', mesAnteriorLabel: ''
  });
  private filtersSubject = new BehaviorSubject<FilterState>({
    days: 90,
    month: null,
    user: null,
    organization: null,
    viewType: 'daily',
    distributionType: 'user'
  });

  private costPerAcu = COST_PER_ACU;

  readonly allData$: Observable<DailyConsumption[]> = this.allDataSubject.asObservable();
  readonly filteredData$: Observable<DailyConsumption[]> = this.filteredDataSubject.asObservable();
  readonly metrics$: Observable<ExecutiveMetrics> = this.metricsSubject.asObservable();
  readonly filters$: Observable<FilterState> = this.filtersSubject.asObservable();

  /**
   * Load raw consumption data (from WebSocket or mock).
   */
  loadData(rawData: DailyConsumption[]): void {
    this.allDataSubject.next(rawData);
    this.recalculate();
  }

  /**
   * Load raw session-level data from the Java backend and normalize it to the
   * internal DailyConsumption shape used by the dashboard.
   */
  loadSessions(sessions: ConsumptionData[], costPerAcu?: number): void {
    if (typeof costPerAcu === 'number' && Number.isFinite(costPerAcu) && costPerAcu > 0) {
      this.costPerAcu = costPerAcu;
    }

    const normalized: DailyConsumption[] = sessions.map(s => {
      const date = this.extractDate(s.timestamp);
      const acus = Number(s.acu_consumed ?? 0);
      return {
        date,
        acus,
        cost: parseFloat((acus * this.costPerAcu).toFixed(2)),
        userId: s.user_id ?? 'unknown',
        organizationId: s.organization_id ?? 'unknown'
      };
    });

    this.loadData(normalized);
  }

  /**
   * Update one or more filter fields and recalculate.
   */
  updateFilters(partial: Partial<FilterState>): void {
    const current = this.filtersSubject.value;
    this.filtersSubject.next({ ...current, ...partial });
    this.applyFilters();
  }

  /**
   * Get the current filter state snapshot.
   */
  getFilters(): FilterState {
    return this.filtersSubject.value;
  }

  /**
   * Get unique months from the data (YYYY-MM format).
   */
  getAvailableMonths(): string[] {
    const months = new Set<string>();
    this.allDataSubject.value.forEach(item => {
      months.add(item.date.substring(0, 7));
    });
    return Array.from(months).sort();
  }

  /**
   * Get unique user IDs from the data.
   */
  getAvailableUsers(): string[] {
    const users = new Set<string>();
    this.allDataSubject.value.forEach(item => {
      users.add(item.userId);
    });
    return Array.from(users).sort();
  }

  /**
   * Get unique organization IDs from the data.
   */
  getAvailableOrganizations(): string[] {
    const orgs = new Set<string>();
    this.allDataSubject.value.forEach(item => {
      orgs.add(item.organizationId);
    });
    return Array.from(orgs).sort();
  }

  /**
   * Get Spanish month name for display.
   */
  getSpanishMonthName(monthIndex: number): string {
    return MONTH_NAMES[monthIndex];
  }

  /**
   * Get formatted month label (e.g., "Septiembre 2025") from YYYY-MM string.
   */
  getMonthLabel(yearMonth: string): string {
    const [year, month] = yearMonth.split('-');
    return `${MONTH_NAMES[parseInt(month, 10) - 1]} ${year}`;
  }

  /**
   * Aggregate daily data by ISO week.
   */
  aggregateByWeek(data: DailyConsumption[]): WeeklyConsumption[] {
    const weeklyMap: Record<string, { acus: number; cost: number }> = {};

    data.forEach(item => {
      const weekKey = this.getISOWeek(item.date);
      if (!weeklyMap[weekKey]) {
        weeklyMap[weekKey] = { acus: 0, cost: 0 };
      }
      weeklyMap[weekKey].acus += item.acus;
      weeklyMap[weekKey].cost += item.cost;
    });

    return Object.keys(weeklyMap)
      .sort()
      .map(week => ({
        date: `Semana ${week}`,
        acus: parseFloat(weeklyMap[week].acus.toFixed(2)),
        cost: parseFloat(weeklyMap[week].cost.toFixed(2))
      }));
  }

  /**
   * Aggregate data by a grouping key (userId or organizationId).
   */
  aggregateByGroup(data: DailyConsumption[], groupBy: 'user' | 'organization'): Record<string, number> {
    const result: Record<string, number> = {};
    data.forEach(item => {
      const key = groupBy === 'user' ? item.userId : item.organizationId;
      if (!result[key]) {
        result[key] = 0;
      }
      result[key] += item.acus;
    });
    return result;
  }

  /**
   * Format currency in USD.
   */
  formatCurrency(value: number): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD'
    }).format(value);
  }

  /**
   * Format number with thousand separators (Spanish locale).
   */
  formatNumber(value: number): string {
    return new Intl.NumberFormat('es-ES', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(value);
  }

  /**
   * Generate mock daily data (mirrors the vanilla JS mock data).
   * Used when WebSocket is not available.
   */
  generateMockData(): DailyConsumption[] {
    const users = ['user-a', 'user-b', 'user-c', 'user-d', 'user-e'];
    const organizations = ['org-alpha', 'org-beta', 'org-gamma'];
    const data: DailyConsumption[] = [];
    const startDate = new Date('2025-09-01');
    const endDate = new Date('2025-11-30');

    for (let d = new Date(startDate); d <= endDate; d.setDate(d.getDate() + 1)) {
      const dateStr = d.toISOString().split('T')[0];
      const numEntries = Math.floor(Math.random() * 3) + 2;

      for (let i = 0; i < numEntries; i++) {
        const baseACUs = 20;
        const variation = Math.random() * 20 - 10;
        const acus = Math.max(10, baseACUs + variation);
        const cost = acus * this.costPerAcu;

        data.push({
          date: dateStr,
          acus: parseFloat(acus.toFixed(2)),
          cost: parseFloat(cost.toFixed(2)),
          userId: users[Math.floor(Math.random() * users.length)],
          organizationId: organizations[Math.floor(Math.random() * organizations.length)]
        });
      }
    }
    return data;
  }

  // ---- Private helpers ----

  private recalculate(): void {
    this.calculateMetrics();
    this.applyFilters();
  }

  /**
   * Calculate executive metrics comparing the two most recent months in the data.
   * Replaces calculateMetricsFromData() from vanilla JS.
   */
  private calculateMetrics(): void {
    const allData = this.allDataSubject.value;

    // Aggregate by date first
    const dailyTotals: Record<string, { acus: number; cost: number }> = {};
    allData.forEach(item => {
      if (!dailyTotals[item.date]) {
        dailyTotals[item.date] = { acus: 0, cost: 0 };
      }
      dailyTotals[item.date].acus += item.acus;
      dailyTotals[item.date].cost += item.cost;
    });

    // Find the two most recent months present in data
    const monthKeys = new Set<string>();
    Object.keys(dailyTotals).forEach(date => monthKeys.add(date.substring(0, 7)));
    const sortedMonths = Array.from(monthKeys).sort();

    if (sortedMonths.length === 0) {
      return; // no data
    }

    const latestMonth = sortedMonths[sortedMonths.length - 1];
    const previousMonth = sortedMonths.length > 1 ? sortedMonths[sortedMonths.length - 2] : null;

    let acusMes = 0, costeMes = 0;
    let acusMesAnterior = 0, costeMesAnterior = 0;

    Object.keys(dailyTotals).forEach(date => {
      if (date.startsWith(latestMonth)) {
        acusMes += dailyTotals[date].acus;
        costeMes += dailyTotals[date].cost;
      }
      if (previousMonth && date.startsWith(previousMonth)) {
        acusMesAnterior += dailyTotals[date].acus;
        costeMesAnterior += dailyTotals[date].cost;
      }
    });

    const diferenciaACUs = acusMes - acusMesAnterior;
    const diferenciaCost = costeMes - costeMesAnterior;
    const porcentajeACUs = acusMesAnterior !== 0 ? (diferenciaACUs / acusMesAnterior) * 100 : 0;
    const porcentajeCost = costeMesAnterior !== 0 ? (diferenciaCost / costeMesAnterior) * 100 : 0;

    const mesActualLabel = this.getMonthLabel(latestMonth);
    const mesAnteriorLabel = previousMonth ? this.getMonthLabel(previousMonth) : 'N/A';

    this.metricsSubject.next({
      acusMes, acusMesAnterior,
      costeMes, costeMesAnterior,
      diferenciaACUs, diferenciaCost,
      porcentajeACUs, porcentajeCost,
      mesActualLabel, mesAnteriorLabel
    });
  }

  private applyFilters(): void {
    const filters = this.filtersSubject.value;
    let data = [...this.allDataSubject.value];

    // Month filter
    if (filters.month) {
      data = data.filter(item => item.date.startsWith(filters.month!));
    }

    // Days filter
    if (filters.days) {
      const allDates = this.allDataSubject.value.map(d => d.date);
      const maxDate = allDates.length > 0
        ? allDates.reduce((max, cur) => (cur > max ? cur : max), allDates[0])
        : new Date().toISOString().split('T')[0];
      const cutoff = new Date(maxDate);
      cutoff.setDate(cutoff.getDate() - filters.days);
      data = data.filter(item => new Date(item.date) >= cutoff);
    }

    // User filter
    if (filters.user) {
      data = data.filter(item => item.userId === filters.user);
    }

    // Organization filter
    if (filters.organization) {
      data = data.filter(item => item.organizationId === filters.organization);
    }

    this.filteredDataSubject.next(data);
  }

  private extractDate(timestamp: string): string {
    if (!timestamp) {
      return new Date().toISOString().split('T')[0];
    }

    // Fast path for ISO-like strings
    if (timestamp.length >= 10 && timestamp[4] === '-' && timestamp[7] === '-') {
      return timestamp.substring(0, 10);
    }

    const d = new Date(timestamp);
    if (!Number.isNaN(d.getTime())) {
      return d.toISOString().split('T')[0];
    }

    return new Date().toISOString().split('T')[0];
  }

  private getISOWeek(dateStr: string): string {
    const d = new Date(dateStr);
    d.setHours(0, 0, 0, 0);
    d.setDate(d.getDate() + 4 - (d.getDay() || 7));
    const yearStart = new Date(d.getFullYear(), 0, 1);
    const weekNo = Math.ceil((((d.getTime() - yearStart.getTime()) / 86400000) + 1) / 7);
    return `${d.getFullYear()}-${String(weekNo).padStart(2, '0')}`;
  }
}

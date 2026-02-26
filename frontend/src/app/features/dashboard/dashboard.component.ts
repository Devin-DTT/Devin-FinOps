import { Component, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';

import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatTabsModule } from '@angular/material/tabs';

import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartData } from 'chart.js';

import { WebSocketService, ConnectionStatus } from '../../core/services/websocket.service';
import {
  WebSocketMessage,
  DevinSession,
  SessionStatus,
  SessionsResponse,
  DashboardState,
  BillingCycle,
  DailyConsumption,
  MetricDataPoint
} from '../../models/devin-data.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatCardModule,
    MatChipsModule,
    MatFormFieldModule,
    MatSelectModule,
    MatIconModule,
    MatToolbarModule,
    MatSortModule,
    MatProgressBarModule,
    MatTooltipModule,
    MatTabsModule,
    BaseChartDirective
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit, OnDestroy {
  @ViewChild(MatSort) sort!: MatSort;

  displayedColumns: string[] = ['session_id', 'title', 'status', 'origin', 'created_at'];
  dataSource = new MatTableDataSource<DevinSession>([]);
  connectionStatus: ConnectionStatus = 'disconnected';

  // Dashboard state aggregated from all endpoints
  state: DashboardState = {
    totalSessions: 0,
    runningSessions: 0,
    finishedSessions: 0,
    failedSessions: 0,
    stoppedSessions: 0,
    sessions: [],
    currentCycleAcu: 0,
    currentCycleLimit: 0,
    billingCycles: [],
    dailyConsumption: [],
    dauCount: 0,
    wauCount: 0,
    mauCount: 0,
    sessionsMetrics: [],
    prsMetrics: [],
    usageMetrics: [],
    searchesMetrics: [],
    activeUsersMetrics: [],
    queueStatus: 'unknown',
    hypervisorCount: 0,
    orgCount: 0,
    userCount: 0,
    lastUpdated: 0
  };

  selectedStatusFilter: SessionStatus | 'all' = 'all';
  statusOptions: Array<SessionStatus | 'all'> = ['all', 'running', 'finished', 'failed', 'stopped', 'suspended', 'blocked'];

  // ACU usage percentage for progress bar
  acuUsagePercent = 0;

  // Chart: Active sessions over time
  lineChartData: ChartData<'line'> = {
    labels: [],
    datasets: [
      {
        data: [],
        label: 'Running Sessions',
        fill: true,
        tension: 0.4,
        borderColor: '#3f51b5',
        backgroundColor: 'rgba(63, 81, 181, 0.1)'
      }
    ]
  };

  lineChartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      x: { title: { display: true, text: 'Time' } },
      y: { title: { display: true, text: 'Running Sessions' }, beginAtZero: true }
    },
    plugins: { legend: { display: true, position: 'top' } }
  };

  // Chart: Session status distribution (doughnut)
  sessionDonutData: ChartData<'doughnut'> = {
    labels: ['Running', 'Finished', 'Failed', 'Stopped'],
    datasets: [
      {
        data: [0, 0, 0, 0],
        backgroundColor: ['#3f51b5', '#4caf50', '#f44336', '#9e9e9e'],
        hoverBackgroundColor: ['#5c6bc0', '#66bb6a', '#ef5350', '#bdbdbd']
      }
    ]
  };

  sessionDonutOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: true, position: 'right' } }
  };

  // Chart: Sessions metrics per day (bar)
  sessionsMetricsChartData: ChartData<'bar'> = {
    labels: [],
    datasets: [
      {
        data: [],
        label: 'Sessions',
        backgroundColor: '#3f51b5',
        borderColor: '#3f51b5',
        borderWidth: 1
      }
    ]
  };

  sessionsMetricsChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      x: { title: { display: true, text: 'Date' } },
      y: { title: { display: true, text: 'Sessions' }, beginAtZero: true }
    },
    plugins: { legend: { display: true, position: 'top' } }
  };

  // Chart: Billing cycles history (bar)
  billingCyclesChartData: ChartData<'bar'> = {
    labels: [],
    datasets: [
      {
        data: [],
        label: 'ACU Usage per Cycle',
        backgroundColor: '#ff9800',
        borderColor: '#ff9800',
        borderWidth: 1
      }
    ]
  };

  billingCyclesChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      x: { title: { display: true, text: 'Billing Cycle' } },
      y: { title: { display: true, text: 'ACU Usage' }, beginAtZero: true }
    },
    plugins: { legend: { display: true, position: 'top' } }
  };

  // Chart: PRs per day (bar)
  prsChartData: ChartData<'bar'> = {
    labels: [],
    datasets: [
      {
        data: [],
        label: 'Pull Requests',
        backgroundColor: '#9c27b0',
        borderColor: '#9c27b0',
        borderWidth: 1
      }
    ]
  };

  prsChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      x: { title: { display: true, text: 'Date' } },
      y: { title: { display: true, text: 'PRs' }, beginAtZero: true }
    },
    plugins: { legend: { display: true, position: 'top' } }
  };

  // Chart: Usage metrics over time (line)
  usageChartData: ChartData<'line'> = {
    labels: [],
    datasets: [
      {
        data: [],
        label: 'Usage',
        fill: true,
        tension: 0.4,
        borderColor: '#00bcd4',
        backgroundColor: 'rgba(0, 188, 212, 0.1)'
      }
    ]
  };

  usageChartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      x: { title: { display: true, text: 'Date' } },
      y: { title: { display: true, text: 'Usage' }, beginAtZero: true }
    },
    plugins: { legend: { display: true, position: 'top' } }
  };

  // Chart: Searches per day (bar)
  searchesChartData: ChartData<'bar'> = {
    labels: [],
    datasets: [
      {
        data: [],
        label: 'Searches',
        backgroundColor: '#009688',
        borderColor: '#009688',
        borderWidth: 1
      }
    ]
  };

  searchesChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      x: { title: { display: true, text: 'Date' } },
      y: { title: { display: true, text: 'Searches' }, beginAtZero: true }
    },
    plugins: { legend: { display: true, position: 'top' } }
  };

  // Chart: Daily ACU consumption
  acuChartData: ChartData<'line'> = {
    labels: [],
    datasets: [
      {
        data: [],
        label: 'ACU Consumed',
        fill: true,
        tension: 0.4,
        borderColor: '#ff9800',
        backgroundColor: 'rgba(255, 152, 0, 0.1)'
      }
    ]
  };

  acuChartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      x: { title: { display: true, text: 'Date' } },
      y: { title: { display: true, text: 'ACU' }, beginAtZero: true }
    },
    plugins: { legend: { display: true, position: 'top' } }
  };

  endpointsReceived = new Set<string>();

  private dataSubscription: Subscription | null = null;
  private statusSubscription: Subscription | null = null;
  private chartHistory: { time: string; count: number }[] = [];
  private readonly maxChartPoints = 30;

  constructor(private wsService: WebSocketService) {}

  ngOnInit(): void {
    this.dataSubscription = this.wsService.data$.subscribe((msg: WebSocketMessage) => {
      this.processMessage(msg);
    });

    this.statusSubscription = this.wsService.connectionStatus$.subscribe((status: ConnectionStatus) => {
      this.connectionStatus = status;
    });
  }

  ngOnDestroy(): void {
    this.dataSubscription?.unsubscribe();
    this.statusSubscription?.unsubscribe();
  }

  onStatusFilterChange(): void {
    this.applyFilter();
  }

  getStatusColor(status: SessionStatus): string {
    const colorMap: Record<string, string> = {
      running: 'primary',
      finished: 'accent',
      failed: 'warn',
      stopped: '',
      suspended: '',
      blocked: 'warn',
      unknown: ''
    };
    return colorMap[status] || '';
  }

  getQueueStatusColor(): string {
    const status = this.state.queueStatus?.toLowerCase();
    if (status === 'normal') return 'accent';
    if (status === 'elevated') return '';
    if (status === 'high') return 'warn';
    return '';
  }

  formatTimestamp(isoString: string): string {
    if (!isoString) return '-';
    const d = new Date(isoString);
    return d.toLocaleString();
  }

  /**
   * Route each incoming WebSocket message to the appropriate handler
   * based on the endpoint name.
   */
  private processMessage(msg: WebSocketMessage): void {
    if (msg.type !== 'data' || !msg.data) return;

    this.endpointsReceived.add(msg.endpoint);
    this.state.lastUpdated = msg.timestamp;

    const data = msg.data as Record<string, unknown>;

    switch (msg.endpoint) {
      case 'list_sessions':
      case 'list_enterprise_sessions':
        this.handleSessions(data);
        break;
      case 'list_billing_cycles':
        this.handleBillingCycles(data);
        break;
      case 'get_daily_consumption':
        this.handleDailyConsumption(data);
        break;
      case 'get_acu_limits':
        this.handleAcuLimits(data);
        break;
      case 'get_dau_metrics':
        this.handleDauMetrics(data);
        break;
      case 'get_wau_metrics':
        this.handleWauMetrics(data);
        break;
      case 'get_mau_metrics':
        this.handleMauMetrics(data);
        break;
      case 'get_sessions_metrics':
        this.handleSessionsMetrics(data);
        break;
      case 'get_prs_metrics':
        this.handlePrsMetrics(data);
        break;
      case 'get_usage_metrics':
        this.handleUsageMetrics(data);
        break;
      case 'get_queue_status':
        this.handleQueueStatus(data);
        break;
      case 'list_hypervisors':
        this.handleHypervisors(data);
        break;
      case 'list_organizations':
        this.handleOrganizations(data);
        break;
      case 'list_users':
        this.handleUsers(data);
        break;
      case 'get_searches_metrics':
        this.handleSearchesMetrics(data);
        break;
      case 'get_active_users_metrics':
        this.handleActiveUsersMetrics(data);
        break;
      default:
        // Other endpoints - log for debugging
        break;
    }
  }

  private handleSessions(data: Record<string, unknown>): void {
    // The Devin API returns sessions in an 'items' array (paginated response)
    let sessionList: DevinSession[];
    if (Array.isArray(data)) {
      sessionList = data as DevinSession[];
    } else if (Array.isArray(data['items'])) {
      sessionList = data['items'] as DevinSession[];
    } else {
      const resp = data as unknown as SessionsResponse;
      sessionList = Array.isArray(resp.sessions) ? resp.sessions : [];
    }

    this.state.sessions = sessionList;
    // Use page length for totalSessions so it stays consistent with status breakdown counts.
    // Both total and breakdown are derived from the same sessionList (current page).
    // Full pagination (fetching all pages) would require backend-side aggregation.
    this.state.totalSessions = sessionList.length;
    this.state.runningSessions = sessionList.filter(s => s.status === 'running').length;
    this.state.finishedSessions = sessionList.filter(s => s.status === 'finished').length;
    this.state.failedSessions = sessionList.filter(s => s.status === 'failed').length;
    this.state.stoppedSessions = sessionList.filter(s => s.status === 'stopped').length;

    this.dataSource.data = sessionList;
    if (this.sort) {
      this.dataSource.sort = this.sort;
    }
    this.applyFilter();
    this.updateSessionChart();
    this.updateSessionDonut();
  }

  private handleBillingCycles(data: Record<string, unknown>): void {
    const cycles = this.extractArray<BillingCycle>(data, 'cycles');
    this.state.billingCycles = cycles;
    if (cycles.length > 0) {
      const current = cycles[cycles.length - 1];
      this.state.currentCycleAcu = current.acu_usage ?? 0;
      this.state.currentCycleLimit = current.acu_limit ?? 0;
      this.acuUsagePercent = this.state.currentCycleLimit > 0
        ? Math.round((this.state.currentCycleAcu / this.state.currentCycleLimit) * 100)
        : 0;
    }
    this.updateBillingCyclesChart();
  }

  private handleDailyConsumption(data: Record<string, unknown>): void {
    // Check for named keys directly (avoid extractArray's fallback picking up wrong array)
    let entries: DailyConsumption[] = [];
    if (Array.isArray(data['daily_consumption'])) {
      entries = data['daily_consumption'] as DailyConsumption[];
    } else if (Array.isArray(data['consumption_by_date'])) {
      // The API returns 'consumption_by_date' with {date: epoch, acus: number}
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
    this.state.dailyConsumption = entries;
    this.updateAcuChart(entries);
  }

  private handleAcuLimits(data: Record<string, unknown>): void {
    const limit = this.extractNumber(data, 'acu_limit') ?? this.extractNumber(data, 'limit');
    if (limit !== null) {
      this.state.currentCycleLimit = limit;
      this.acuUsagePercent = this.state.currentCycleLimit > 0
        ? Math.round((this.state.currentCycleAcu / this.state.currentCycleLimit) * 100)
        : 0;
    }
  }

  private handleDauMetrics(data: Record<string, unknown>): void {
    this.state.dauCount = this.extractMetricCount(data);
  }

  private handleWauMetrics(data: Record<string, unknown>): void {
    this.state.wauCount = this.extractMetricCount(data);
  }

  private handleMauMetrics(data: Record<string, unknown>): void {
    this.state.mauCount = this.extractMetricCount(data);
  }

  private handleSessionsMetrics(data: Record<string, unknown>): void {
    this.state.sessionsMetrics = this.normalizeMetricTimeSeries(data, 'sessions');
    this.updateSessionsMetricsChart();
  }

  private handlePrsMetrics(data: Record<string, unknown>): void {
    this.state.prsMetrics = this.normalizeMetricTimeSeries(data, 'prs');
    this.updatePrsChart();
  }

  private handleUsageMetrics(data: Record<string, unknown>): void {
    this.state.usageMetrics = this.normalizeMetricTimeSeries(data, 'usage');
    this.updateUsageChart();
  }

  private handleSearchesMetrics(data: Record<string, unknown>): void {
    this.state.searchesMetrics = this.normalizeMetricTimeSeries(data, 'searches');
    this.updateSearchesChart();
  }

  private handleActiveUsersMetrics(data: Record<string, unknown>): void {
    this.state.activeUsersMetrics = this.normalizeMetricTimeSeries(data, 'active_users');
  }

  private handleQueueStatus(data: Record<string, unknown>): void {
    this.state.queueStatus = (data['status'] as string) ?? 'unknown';
  }

  private handleHypervisors(data: Record<string, unknown>): void {
    // API returns paginated { items: [...], total: N } — prefer total over page length
    this.state.hypervisorCount = typeof data['total'] === 'number'
      ? (data['total'] as number)
      : this.extractArray<unknown>(data, 'hypervisors').length;
  }

  private handleOrganizations(data: Record<string, unknown>): void {
    // API returns paginated { items: [...], total: N } — prefer total over page length
    this.state.orgCount = typeof data['total'] === 'number'
      ? (data['total'] as number)
      : this.extractArray<unknown>(data, 'organizations').length;
  }

  private handleUsers(data: Record<string, unknown>): void {
    // API returns paginated { items: [...], total: N } — prefer total over page length
    this.state.userCount = typeof data['total'] === 'number'
      ? (data['total'] as number)
      : this.extractArray<unknown>(data, 'users').length;
  }

  // --- Helpers ---

  private extractArray<T>(data: Record<string, unknown>, key: string): T[] {
    if (Array.isArray(data)) return data as T[];
    const value = data[key];
    if (Array.isArray(value)) return value as T[];
    // Try first key that is an array
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

  private extractMetricCount(data: Record<string, unknown>): number {
    // Metric endpoints may return { count: N }, { value: N }, { data: [...] },
    // a bare array of time-series entries, or a paginated { items: [...], total: N }
    if (typeof data['count'] === 'number') return data['count'] as number;
    if (typeof data['value'] === 'number') return data['value'] as number;
    // Handle bare array of time-series entries (DAU/WAU/MAU may return [{active_users: N}, ...])
    if (Array.isArray(data)) {
      return this.extractLastMetricFromArray(data as Record<string, unknown>[]);
    }
    // Handle paginated object { items: [...], total: N } or { data: [...] }
    const arr = Array.isArray(data['items'])
      ? data['items'] as Record<string, unknown>[]
      : (Array.isArray(data['data']) ? data['data'] as Record<string, unknown>[] : []);
    if (arr.length > 0) {
      return this.extractLastMetricFromArray(arr);
    }
    // Final fallback via extractArray's greedy heuristic
    const fallback = this.extractArray<MetricDataPoint>(data, 'data');
    if (fallback.length > 0) {
      const last = fallback[fallback.length - 1];
      return (last.count ?? last.value) ?? 0;
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

  /**
   * Normalizes metrics time-series data from the Devin API.
   * The API returns arrays like [{start_time: epoch, end_time: epoch, active_users: N}, ...]
   * This converts them to MetricDataPoint[] with a date string and count.
   */
  private normalizeMetricTimeSeries(
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

  private applyFilter(): void {
    if (this.selectedStatusFilter === 'all') {
      this.dataSource.filter = '';
    } else {
      this.dataSource.filterPredicate = (session: DevinSession, filter: string) => {
        return session.status === filter;
      };
      this.dataSource.filter = this.selectedStatusFilter;
    }
  }

  private updateSessionChart(): void {
    const now = new Date();
    const timeLabel = now.toLocaleTimeString();

    this.chartHistory.push({ time: timeLabel, count: this.state.runningSessions });

    if (this.chartHistory.length > this.maxChartPoints) {
      this.chartHistory.shift();
    }

    this.lineChartData = {
      ...this.lineChartData,
      labels: this.chartHistory.map(h => h.time),
      datasets: [
        {
          ...this.lineChartData.datasets[0],
          data: this.chartHistory.map(h => h.count)
        }
      ]
    };
  }

  private updateAcuChart(entries: DailyConsumption[]): void {
    const sorted = [...entries].sort((a, b) => a.date.localeCompare(b.date));
    this.acuChartData = {
      ...this.acuChartData,
      labels: sorted.map(e => e.date),
      datasets: [
        {
          ...this.acuChartData.datasets[0],
          data: sorted.map(e => e.acu_consumed)
        }
      ]
    };
  }

  private updateSessionDonut(): void {
    this.sessionDonutData = {
      ...this.sessionDonutData,
      datasets: [
        {
          ...this.sessionDonutData.datasets[0],
          data: [
            this.state.runningSessions,
            this.state.finishedSessions,
            this.state.failedSessions,
            this.state.stoppedSessions
          ]
        }
      ]
    };
  }

  private updateSessionsMetricsChart(): void {
    const metrics = this.state.sessionsMetrics;
    this.sessionsMetricsChartData = {
      ...this.sessionsMetricsChartData,
      labels: metrics.map(m => m.date ?? ''),
      datasets: [
        {
          ...this.sessionsMetricsChartData.datasets[0],
          data: metrics.map(m => (m.count ?? m.value) ?? 0)
        }
      ]
    };
  }

  private updateBillingCyclesChart(): void {
    const cycles = this.state.billingCycles;
    this.billingCyclesChartData = {
      ...this.billingCyclesChartData,
      labels: cycles.map(c => c.start_date),
      datasets: [
        {
          ...this.billingCyclesChartData.datasets[0],
          data: cycles.map(c => c.acu_usage ?? 0)
        }
      ]
    };
  }

  private updatePrsChart(): void {
    const metrics = this.state.prsMetrics;
    this.prsChartData = {
      ...this.prsChartData,
      labels: metrics.map(m => m.date ?? ''),
      datasets: [
        {
          ...this.prsChartData.datasets[0],
          data: metrics.map(m => (m.count ?? m.value) ?? 0)
        }
      ]
    };
  }

  private updateUsageChart(): void {
    const metrics = this.state.usageMetrics;
    this.usageChartData = {
      ...this.usageChartData,
      labels: metrics.map(m => m.date ?? ''),
      datasets: [
        {
          ...this.usageChartData.datasets[0],
          data: metrics.map(m => (m.count ?? m.value) ?? 0)
        }
      ]
    };
  }

  private updateSearchesChart(): void {
    const metrics = this.state.searchesMetrics;
    this.searchesChartData = {
      ...this.searchesChartData,
      labels: metrics.map(m => m.date ?? ''),
      datasets: [
        {
          ...this.searchesChartData.datasets[0],
          data: metrics.map(m => (m.count ?? m.value) ?? 0)
        }
      ]
    };
  }
}

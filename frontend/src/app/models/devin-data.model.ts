/**
 * Raw WebSocket message from the backend.
 * Each message wraps one endpoint's API response.
 */
export interface WebSocketMessage {
  type: 'data' | 'error' | 'ack';
  endpoint: string;
  timestamp: number;
  data: unknown;
}

/**
 * Session as returned by the Devin API (list_sessions / list_enterprise_sessions).
 */
export type SessionStatus = 'running' | 'finished' | 'failed' | 'stopped' | 'suspended' | 'blocked' | 'unknown';

export interface DevinSession {
  session_id: string;
  title?: string;
  status: SessionStatus;
  status_enum?: string;
  created_at: string;
  updated_at?: string;
  is_archived?: boolean;
  snap_count?: number;
  total_tokens?: number;
  origin?: string;
  pull_request?: {
    url?: string;
    status?: string;
  };
}

/**
 * Response shape from list_sessions / list_enterprise_sessions endpoints.
 */
export interface SessionsResponse {
  sessions: DevinSession[];
  has_more?: boolean;
  next_cursor?: string;
  total_count?: number;
}

/**
 * Billing cycle from list_billing_cycles endpoint.
 */
export interface BillingCycle {
  cycle_id?: string;
  start_date: string;
  end_date: string;
  acu_usage?: number;
  acu_limit?: number;
  status?: string;
}

/**
 * Daily consumption entry from get_daily_consumption endpoint.
 */
export interface DailyConsumption {
  date: string;
  acu_consumed: number;
  session_count?: number;
}

/**
 * Metrics response (DAU, WAU, MAU, sessions, PRs, usage, searches).
 */
export interface MetricsResponse {
  data?: MetricDataPoint[];
  [key: string]: unknown;
}

export interface MetricDataPoint {
  date?: string;
  value?: number;
  count?: number;
  [key: string]: unknown;
}

/**
 * Queue status from get_queue_status endpoint.
 */
export interface QueueStatus {
  status: string;
  queue_length?: number;
  estimated_wait?: string;
}

/**
 * Aggregated dashboard state built from multiple endpoint responses.
 */
export interface DashboardState {
  // Session KPIs
  totalSessions: number;
  runningSessions: number;
  finishedSessions: number;
  failedSessions: number;
  stoppedSessions: number;

  // Sessions table
  sessions: DevinSession[];

  // Consumption / Billing
  currentCycleAcu: number;
  currentCycleLimit: number;
  billingCycles: BillingCycle[];
  dailyConsumption: DailyConsumption[];

  // Metrics
  dauCount: number;
  wauCount: number;
  mauCount: number;
  sessionsMetrics: MetricDataPoint[];
  prsMetrics: MetricDataPoint[];
  usageMetrics: MetricDataPoint[];

  // Infrastructure
  queueStatus: string;
  hypervisorCount: number;

  // Organizations & Users
  orgCount: number;
  userCount: number;

  // Timestamp of last update
  lastUpdated: number;
}

/**
 * Models for FinOps metrics data.
 * Mirrors the Java MetricsResult / ConsumptionData structures.
 */

export interface MetricsResult {
  config: MetricsConfig;
  reporting_period: ReportingPeriod;
  metrics: MetricsData;
}

export interface MetricsConfig {
  price_per_acu: number;
  currency: string;
  working_hours_per_day: number;
  working_days_per_month: number;
}

export interface ReportingPeriod {
  start_date: string;
  end_date: string;
  month: string;
}

export interface MetricsData {
  '01_total_monthly_cost': number;
  '02_total_acus': number;
  '03_cost_per_user': Record<string, number>;
  '04_acus_per_session': Record<string, number>;
  '05_average_acus_per_session': number;
  '06_total_sessions': number;
  '07_sessions_per_user': Record<string, number>;
  '08_total_duration_minutes': number;
  '09_average_session_duration': number;
  '10_acus_per_minute': number;
  '11_cost_per_minute': number;
  '12_unique_users': number;
  '13_sessions_by_task_type': Record<string, number>;
  '14_acus_by_task_type': Record<string, number>;
  '15_cost_by_task_type': Record<string, number>;
  '16_sessions_by_department': Record<string, number>;
  '17_acus_by_department': Record<string, number>;
  '18_cost_by_department': Record<string, number>;
  '19_average_cost_per_user': number;
  '20_efficiency_ratio': number;
}

export interface WebSocketMessage {
  type: 'connected' | 'status' | 'metrics' | 'error';
  message?: string;
  sessionId?: string;
  data?: MetricsResult;
}

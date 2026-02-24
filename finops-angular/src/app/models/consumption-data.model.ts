/**
 * Represents a single consumption record from the Java backend.
 * Maps to ConsumptionData.java in finops-service.
 */
export interface ConsumptionData {
  session_id: string;
  user_id: string;
  organization_id: string;
  project_id: string;
  pull_request_id: string;
  timestamp: string;
  acu_consumed: number;
  business_unit: string;
  task_type: string;
  is_out_of_hours: boolean;
  is_merged: boolean;
  session_outcome: string;
}

/**
 * Normalized daily data for charting (aggregated from ConsumptionData).
 */
export interface DailyConsumption {
  date: string;
  acus: number;
  cost: number;
  userId: string;
  organizationId: string;
}

/**
 * Weekly aggregated data for the weekly chart view.
 */
export interface WeeklyConsumption {
  date: string;
  acus: number;
  cost: number;
}

/**
 * Executive metrics comparing current month vs previous month.
 */
export interface ExecutiveMetrics {
  acusMes: number;
  acusMesAnterior: number;
  costeMes: number;
  costeMesAnterior: number;
  diferenciaACUs: number;
  diferenciaCost: number;
  porcentajeACUs: number;
  porcentajeCost: number;
  mesActualLabel?: string;
  mesAnteriorLabel?: string;
}

/**
 * Filter state for the dashboard.
 */
export interface FilterState {
  days: number | null;
  month: string | null;
  user: string | null;
  organization: string | null;
  viewType: 'daily' | 'weekly';
  distributionType: 'user' | 'organization';
}

/**
 * WebSocket message envelope from the Java backend.
 */
export interface WsMessage {
  type: 'connected' | 'status' | 'metrics' | 'error';
  message?: string;
  sessionId?: string;
  data?: MetricsPayload;
  sessions?: ConsumptionData[];
}

/**
 * Metrics result payload from the backend (MetricsResult.java).
 */
export interface MetricsPayload {
  config: Record<string, unknown>;
  reportingPeriod: Record<string, string>;
  metrics: Record<string, unknown>;
}

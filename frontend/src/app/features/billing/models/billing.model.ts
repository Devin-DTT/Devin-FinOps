export interface BillingCycle {
  cycle_id?: string;
  start_date: string;
  end_date: string;
  acu_usage?: number;
  acu_limit?: number;
  status?: string;
}

export interface DailyConsumption {
  date: string;
  acu_consumed: number;
  session_count?: number;
}

export interface FinOpsKpis {
  currentCycleAcu: number;
  currentCycleLimit: number;
  acuUsagePercent: number;
  acuPerUser: number;
  acuPerSession: number;
  projectedEndOfCycleAcu: number;
  userCount: number;
  totalSessions: number;
}

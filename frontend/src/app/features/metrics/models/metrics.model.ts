export interface MetricDataPoint {
  date?: string;
  value?: number;
  count?: number;
  [key: string]: unknown;
}

export interface MetricsResponse {
  data?: MetricDataPoint[];
  [key: string]: unknown;
}

import { MetricsResult } from '../models/metrics.model';

/**
 * Shared test fixtures for Angular tests.
 * Mirrors the sample data patterns from the Python and Java tests.
 */

export const SAMPLE_METRICS_RESULT: MetricsResult = {
  config: {
    price_per_acu: 0.05,
    currency: 'USD',
    working_hours_per_day: 8,
    working_days_per_month: 22
  },
  reporting_period: {
    start_date: '2024-09-01',
    end_date: '2024-09-30',
    month: '2024-09'
  },
  metrics: {
    '01_total_monthly_cost': 62.50,
    '02_total_acus': 1250.0,
    '03_cost_per_user': { 'user1': 25.00, 'user2': 20.00, 'user3': 17.50 },
    '04_acus_per_session': { 'session-1': 500, 'session-2': 300, 'session-3': 200, 'session-4': 150, 'session-5': 100 },
    '05_average_acus_per_session': 250.0,
    '06_total_sessions': 5,
    '07_sessions_per_user': { 'user1': 2, 'user2': 2, 'user3': 1 },
    '08_total_duration_minutes': 450,
    '09_average_session_duration': 90.0,
    '10_acus_per_minute': 2.78,
    '11_cost_per_minute': 0.139,
    '12_unique_users': 3,
    '13_sessions_by_task_type': { 'development': 3, 'review': 2 },
    '14_acus_by_task_type': { 'development': 800, 'review': 450 },
    '15_cost_by_task_type': { 'development': 40.00, 'review': 22.50 },
    '16_sessions_by_department': { 'engineering': 4, 'qa': 1 },
    '17_acus_by_department': { 'engineering': 1050, 'qa': 200 },
    '18_cost_by_department': { 'engineering': 52.50, 'qa': 10.00 },
    '19_average_cost_per_user': 20.83,
    '20_efficiency_ratio': 0.85
  }
};

export const EMPTY_METRICS_RESULT: MetricsResult = {
  config: {
    price_per_acu: 0.05,
    currency: 'USD',
    working_hours_per_day: 8,
    working_days_per_month: 22
  },
  reporting_period: {
    start_date: '2024-09-01',
    end_date: '2024-09-30',
    month: '2024-09'
  },
  metrics: {
    '01_total_monthly_cost': 0,
    '02_total_acus': 0,
    '03_cost_per_user': {},
    '04_acus_per_session': {},
    '05_average_acus_per_session': 0,
    '06_total_sessions': 0,
    '07_sessions_per_user': {},
    '08_total_duration_minutes': 0,
    '09_average_session_duration': 0,
    '10_acus_per_minute': 0,
    '11_cost_per_minute': 0,
    '12_unique_users': 0,
    '13_sessions_by_task_type': {},
    '14_acus_by_task_type': {},
    '15_cost_by_task_type': {},
    '16_sessions_by_department': {},
    '17_acus_by_department': {},
    '18_cost_by_department': {},
    '19_average_cost_per_user': 0,
    '20_efficiency_ratio': 0
  }
};

export const SINGLE_USER_METRICS_RESULT: MetricsResult = {
  config: {
    price_per_acu: 0.10,
    currency: 'EUR',
    working_hours_per_day: 8,
    working_days_per_month: 22
  },
  reporting_period: {
    start_date: '2024-10-01',
    end_date: '2024-10-31',
    month: '2024-10'
  },
  metrics: {
    '01_total_monthly_cost': 100.0,
    '02_total_acus': 1000.0,
    '03_cost_per_user': { 'solo-user': 100.0 },
    '04_acus_per_session': { 'session-1': 400, 'session-2': 600 },
    '05_average_acus_per_session': 500.0,
    '06_total_sessions': 2,
    '07_sessions_per_user': { 'solo-user': 2 },
    '08_total_duration_minutes': 120,
    '09_average_session_duration': 60.0,
    '10_acus_per_minute': 8.33,
    '11_cost_per_minute': 0.833,
    '12_unique_users': 1,
    '13_sessions_by_task_type': { 'development': 2 },
    '14_acus_by_task_type': { 'development': 1000 },
    '15_cost_by_task_type': { 'development': 100.0 },
    '16_sessions_by_department': { 'engineering': 2 },
    '17_acus_by_department': { 'engineering': 1000 },
    '18_cost_by_department': { 'engineering': 100.0 },
    '19_average_cost_per_user': 100.0,
    '20_efficiency_ratio': 0.90
  }
};

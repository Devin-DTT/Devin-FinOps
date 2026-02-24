import { TestBed } from '@angular/core/testing';
import { MetricsService } from './metrics.service';
import { MetricsResult } from '../models/metrics.model';
import {
  SAMPLE_METRICS_RESULT,
  EMPTY_METRICS_RESULT,
  SINGLE_USER_METRICS_RESULT
} from '../testing/metrics-fixtures';

/**
 * Comprehensive unit tests for MetricsService.
 * Mirrors the Python TestMetricsCalculatorBasic and Java MetricsServiceTest coverage.
 */
describe('MetricsService', () => {
  let service: MetricsService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(MetricsService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  // ========== getMetricValue Tests ==========
  describe('getMetricValue', () => {
    it('should return null for null metrics', () => {
      expect(service.getMetricValue(null, '01_total_monthly_cost')).toBeNull();
    });

    it('should return numeric metric value', () => {
      const value = service.getMetricValue(SAMPLE_METRICS_RESULT, '01_total_monthly_cost');
      expect(value).toBe(62.50);
    });

    it('should return object metric value (cost_per_user)', () => {
      const value = service.getMetricValue(SAMPLE_METRICS_RESULT, '03_cost_per_user');
      expect(typeof value).toBe('object');
      expect(value).toEqual({ 'user1': 25.00, 'user2': 20.00, 'user3': 17.50 });
    });

    it('should return zero for empty metrics', () => {
      const value = service.getMetricValue(EMPTY_METRICS_RESULT, '02_total_acus');
      expect(value).toBe(0);
    });
  });

  // ========== Total Cost Tests ==========
  describe('getTotalCost', () => {
    it('should return total cost from sample metrics', () => {
      expect(service.getTotalCost(SAMPLE_METRICS_RESULT)).toBe(62.50);
    });

    it('should return 0 for null metrics', () => {
      expect(service.getTotalCost(null)).toBe(0);
    });

    it('should return 0 for empty metrics', () => {
      expect(service.getTotalCost(EMPTY_METRICS_RESULT)).toBe(0);
    });

    it('should return correct cost for single user', () => {
      expect(service.getTotalCost(SINGLE_USER_METRICS_RESULT)).toBe(100.0);
    });
  });

  // ========== Total ACUs Tests ==========
  describe('getTotalAcus', () => {
    it('should return total ACUs from sample metrics', () => {
      expect(service.getTotalAcus(SAMPLE_METRICS_RESULT)).toBe(1250.0);
    });

    it('should return 0 for null metrics', () => {
      expect(service.getTotalAcus(null)).toBe(0);
    });

    it('should return 0 for empty metrics', () => {
      expect(service.getTotalAcus(EMPTY_METRICS_RESULT)).toBe(0);
    });

    it('should return correct ACUs for single user', () => {
      expect(service.getTotalAcus(SINGLE_USER_METRICS_RESULT)).toBe(1000.0);
    });
  });

  // ========== Total Sessions Tests ==========
  describe('getTotalSessions', () => {
    it('should return total sessions from sample metrics', () => {
      expect(service.getTotalSessions(SAMPLE_METRICS_RESULT)).toBe(5);
    });

    it('should return 0 for null metrics', () => {
      expect(service.getTotalSessions(null)).toBe(0);
    });

    it('should return 0 for empty metrics', () => {
      expect(service.getTotalSessions(EMPTY_METRICS_RESULT)).toBe(0);
    });
  });

  // ========== Unique Users Tests ==========
  describe('getUniqueUsers', () => {
    it('should return unique users from sample metrics', () => {
      expect(service.getUniqueUsers(SAMPLE_METRICS_RESULT)).toBe(3);
    });

    it('should return 0 for null metrics', () => {
      expect(service.getUniqueUsers(null)).toBe(0);
    });

    it('should return 1 for single user metrics', () => {
      expect(service.getUniqueUsers(SINGLE_USER_METRICS_RESULT)).toBe(1);
    });
  });

  // ========== Cost Per User Tests ==========
  describe('getCostPerUser', () => {
    it('should return cost per user map', () => {
      const result = service.getCostPerUser(SAMPLE_METRICS_RESULT);
      expect(Object.keys(result).length).toBe(3);
      expect(result['user1']).toBe(25.00);
      expect(result['user2']).toBe(20.00);
      expect(result['user3']).toBe(17.50);
    });

    it('should return empty object for null metrics', () => {
      const result = service.getCostPerUser(null);
      expect(result).toEqual({});
    });

    it('should return empty object for empty metrics', () => {
      const result = service.getCostPerUser(EMPTY_METRICS_RESULT);
      expect(result).toEqual({});
    });

    it('should return single user cost for single user', () => {
      const result = service.getCostPerUser(SINGLE_USER_METRICS_RESULT);
      expect(Object.keys(result).length).toBe(1);
      expect(result['solo-user']).toBe(100.0);
    });
  });

  // ========== Average ACUs Per Session Tests ==========
  describe('getAverageAcusPerSession', () => {
    it('should return average ACUs per session', () => {
      expect(service.getAverageAcusPerSession(SAMPLE_METRICS_RESULT)).toBe(250.0);
    });

    it('should return 0 for null metrics', () => {
      expect(service.getAverageAcusPerSession(null)).toBe(0);
    });

    it('should return correct average for single user', () => {
      expect(service.getAverageAcusPerSession(SINGLE_USER_METRICS_RESULT)).toBe(500.0);
    });
  });

  // ========== Consistency Validation Tests ==========
  describe('validateCostConsistency', () => {
    it('should return true for consistent metrics (cost = acus * price)', () => {
      expect(service.validateCostConsistency(SAMPLE_METRICS_RESULT)).toBeTrue();
    });

    it('should return false for null metrics', () => {
      expect(service.validateCostConsistency(null)).toBeFalse();
    });

    it('should return true for single user metrics', () => {
      expect(service.validateCostConsistency(SINGLE_USER_METRICS_RESULT)).toBeTrue();
    });

    it('should return true for empty metrics (0 = 0 * price)', () => {
      expect(service.validateCostConsistency(EMPTY_METRICS_RESULT)).toBeTrue();
    });

    it('should return false for inconsistent metrics', () => {
      const inconsistent: MetricsResult = JSON.parse(JSON.stringify(SAMPLE_METRICS_RESULT));
      inconsistent.metrics['01_total_monthly_cost'] = 999.99; // Wrong cost
      expect(service.validateCostConsistency(inconsistent)).toBeFalse();
    });
  });

  // ========== Cost Per User Sum Validation Tests ==========
  describe('validateCostPerUserSumsToTotal', () => {
    it('should return true when per-user costs sum to total', () => {
      expect(service.validateCostPerUserSumsToTotal(SAMPLE_METRICS_RESULT)).toBeTrue();
    });

    it('should return false for null metrics', () => {
      expect(service.validateCostPerUserSumsToTotal(null)).toBeFalse();
    });

    it('should return true for single user (single cost = total)', () => {
      expect(service.validateCostPerUserSumsToTotal(SINGLE_USER_METRICS_RESULT)).toBeTrue();
    });

    it('should return true for empty metrics (0 sums to 0)', () => {
      expect(service.validateCostPerUserSumsToTotal(EMPTY_METRICS_RESULT)).toBeTrue();
    });

    it('should return false when per-user costs do not sum to total', () => {
      const inconsistent: MetricsResult = JSON.parse(JSON.stringify(SAMPLE_METRICS_RESULT));
      inconsistent.metrics['03_cost_per_user'] = { 'user1': 1.00 }; // Wrong sum
      expect(service.validateCostPerUserSumsToTotal(inconsistent)).toBeFalse();
    });
  });

  // ========== Format Currency Tests ==========
  describe('formatCurrency', () => {
    it('should format currency with USD', () => {
      expect(service.formatCurrency(62.50)).toBe('62.50 USD');
    });

    it('should format zero', () => {
      expect(service.formatCurrency(0)).toBe('0.00 USD');
    });

    it('should format with custom currency', () => {
      expect(service.formatCurrency(100.0, 'EUR')).toBe('100.00 EUR');
    });

    it('should format large numbers', () => {
      expect(service.formatCurrency(12345.678)).toBe('12345.68 USD');
    });

    it('should format small numbers', () => {
      expect(service.formatCurrency(0.001)).toBe('0.00 USD');
    });
  });

  // ========== Format Duration Tests ==========
  describe('formatDuration', () => {
    it('should format minutes under 60', () => {
      expect(service.formatDuration(45)).toBe('45 min');
    });

    it('should format exactly 60 minutes', () => {
      expect(service.formatDuration(60)).toBe('1h 0m');
    });

    it('should format hours and minutes', () => {
      expect(service.formatDuration(90)).toBe('1h 30m');
    });

    it('should format zero minutes', () => {
      expect(service.formatDuration(0)).toBe('0 min');
    });

    it('should format large duration', () => {
      expect(service.formatDuration(450)).toBe('7h 30m');
    });
  });

  // ========== All 20 Metrics Coverage Tests ==========
  describe('All 20 Metrics Coverage', () => {
    it('should access metric 01: total_monthly_cost', () => {
      expect(service.getMetricValue(SAMPLE_METRICS_RESULT, '01_total_monthly_cost')).toBe(62.50);
    });

    it('should access metric 02: total_acus', () => {
      expect(service.getMetricValue(SAMPLE_METRICS_RESULT, '02_total_acus')).toBe(1250.0);
    });

    it('should access metric 03: cost_per_user', () => {
      const value = service.getMetricValue(SAMPLE_METRICS_RESULT, '03_cost_per_user');
      expect(value).toEqual({ 'user1': 25.00, 'user2': 20.00, 'user3': 17.50 });
    });

    it('should access metric 04: acus_per_session', () => {
      const value = service.getMetricValue(SAMPLE_METRICS_RESULT, '04_acus_per_session');
      expect(value).toBeTruthy();
    });

    it('should access metric 05: average_acus_per_session', () => {
      expect(service.getMetricValue(SAMPLE_METRICS_RESULT, '05_average_acus_per_session')).toBe(250.0);
    });

    it('should access metric 06: total_sessions', () => {
      expect(service.getMetricValue(SAMPLE_METRICS_RESULT, '06_total_sessions')).toBe(5);
    });

    it('should access metric 07: sessions_per_user', () => {
      const value = service.getMetricValue(SAMPLE_METRICS_RESULT, '07_sessions_per_user');
      expect(value).toEqual({ 'user1': 2, 'user2': 2, 'user3': 1 });
    });

    it('should access metric 08: total_duration_minutes', () => {
      expect(service.getMetricValue(SAMPLE_METRICS_RESULT, '08_total_duration_minutes')).toBe(450);
    });

    it('should access metric 09: average_session_duration', () => {
      expect(service.getMetricValue(SAMPLE_METRICS_RESULT, '09_average_session_duration')).toBe(90.0);
    });

    it('should access metric 10: acus_per_minute', () => {
      expect(service.getMetricValue(SAMPLE_METRICS_RESULT, '10_acus_per_minute')).toBe(2.78);
    });

    it('should access metric 11: cost_per_minute', () => {
      expect(service.getMetricValue(SAMPLE_METRICS_RESULT, '11_cost_per_minute')).toBe(0.139);
    });

    it('should access metric 12: unique_users', () => {
      expect(service.getMetricValue(SAMPLE_METRICS_RESULT, '12_unique_users')).toBe(3);
    });

    it('should access metric 13: sessions_by_task_type', () => {
      const value = service.getMetricValue(SAMPLE_METRICS_RESULT, '13_sessions_by_task_type');
      expect(value).toEqual({ 'development': 3, 'review': 2 });
    });

    it('should access metric 14: acus_by_task_type', () => {
      const value = service.getMetricValue(SAMPLE_METRICS_RESULT, '14_acus_by_task_type');
      expect(value).toEqual({ 'development': 800, 'review': 450 });
    });

    it('should access metric 15: cost_by_task_type', () => {
      const value = service.getMetricValue(SAMPLE_METRICS_RESULT, '15_cost_by_task_type');
      expect(value).toEqual({ 'development': 40.00, 'review': 22.50 });
    });

    it('should access metric 16: sessions_by_department', () => {
      const value = service.getMetricValue(SAMPLE_METRICS_RESULT, '16_sessions_by_department');
      expect(value).toEqual({ 'engineering': 4, 'qa': 1 });
    });

    it('should access metric 17: acus_by_department', () => {
      const value = service.getMetricValue(SAMPLE_METRICS_RESULT, '17_acus_by_department');
      expect(value).toEqual({ 'engineering': 1050, 'qa': 200 });
    });

    it('should access metric 18: cost_by_department', () => {
      const value = service.getMetricValue(SAMPLE_METRICS_RESULT, '18_cost_by_department');
      expect(value).toEqual({ 'engineering': 52.50, 'qa': 10.00 });
    });

    it('should access metric 19: average_cost_per_user', () => {
      expect(service.getMetricValue(SAMPLE_METRICS_RESULT, '19_average_cost_per_user')).toBe(20.83);
    });

    it('should access metric 20: efficiency_ratio', () => {
      expect(service.getMetricValue(SAMPLE_METRICS_RESULT, '20_efficiency_ratio')).toBe(0.85);
    });
  });
});

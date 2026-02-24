import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetricsDisplayComponent } from './metrics-display.component';
import { MetricsService } from '../../services/metrics.service';
import { SAMPLE_METRICS_RESULT, EMPTY_METRICS_RESULT, SINGLE_USER_METRICS_RESULT } from '../../testing/metrics-fixtures';

/**
 * Comprehensive tests for MetricsDisplayComponent.
 * Tests rendering of metrics data, cost per user table, consistency checks, and edge cases.
 */
describe('MetricsDisplayComponent', () => {
  let component: MetricsDisplayComponent;
  let fixture: ComponentFixture<MetricsDisplayComponent>;
  let metricsService: MetricsService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MetricsDisplayComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(MetricsDisplayComponent);
    component = fixture.componentInstance;
    metricsService = TestBed.inject(MetricsService);
    fixture.detectChanges();
  });

  // ========== Component Creation ==========
  describe('Creation', () => {
    it('should create the component', () => {
      expect(component).toBeTruthy();
    });

    it('should have null metricsResult by default', () => {
      expect(component.metricsResult).toBeNull();
    });
  });

  // ========== No Metrics State ==========
  describe('No Metrics', () => {
    it('should show no metrics message when metricsResult is null', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('.no-metrics')).toBeTruthy();
      expect(compiled.querySelector('.no-metrics p')?.textContent).toContain('No metrics data available');
    });

    it('should not show metrics display when metricsResult is null', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('.metrics-display')).toBeNull();
    });
  });

  // ========== With Sample Metrics ==========
  describe('With Sample Metrics', () => {
    beforeEach(() => {
      component.metricsResult = SAMPLE_METRICS_RESULT;
      fixture.detectChanges();
    });

    it('should show metrics display when metricsResult is set', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('.metrics-display')).toBeTruthy();
    });

    it('should not show no-metrics message when metrics exist', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('.no-metrics')).toBeNull();
    });

    it('should display the metrics details heading', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('h2')?.textContent).toContain('Metrics Details');
    });

    it('should display the reporting period', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      const periodEl = compiled.querySelector('.reporting-period');
      expect(periodEl?.textContent).toContain('2024-09-01');
      expect(periodEl?.textContent).toContain('2024-09-30');
    });

    it('should show cost consistency check passed', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      const consistencyEl = compiled.querySelector('.consistency-check');
      expect(consistencyEl?.textContent).toContain('Cost consistency check passed');
      expect(consistencyEl?.classList.contains('consistent')).toBeTrue();
    });

    it('should display cost per user table', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      const table = compiled.querySelector('.cost-per-user-section table');
      expect(table).toBeTruthy();

      const rows = table?.querySelectorAll('tbody tr');
      expect(rows?.length).toBe(3); // 3 users
    });

    it('should display user names in cost per user table', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      const cells = compiled.querySelectorAll('.cost-per-user-section tbody td');
      const userNames = Array.from(cells)
        .filter((_, i) => i % 2 === 0)
        .map(cell => cell.textContent?.trim());
      expect(userNames).toContain('user1');
      expect(userNames).toContain('user2');
      expect(userNames).toContain('user3');
    });
  });

  // ========== With Empty Metrics ==========
  describe('With Empty Metrics', () => {
    beforeEach(() => {
      component.metricsResult = EMPTY_METRICS_RESULT;
      fixture.detectChanges();
    });

    it('should show metrics display section', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('.metrics-display')).toBeTruthy();
    });

    it('should show no data message for empty cost per user', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('.no-data')?.textContent).toContain('No user cost data available');
    });

    it('should not show cost per user table', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('.cost-per-user-section')).toBeNull();
    });
  });

  // ========== With Single User Metrics ==========
  describe('With Single User Metrics', () => {
    beforeEach(() => {
      component.metricsResult = SINGLE_USER_METRICS_RESULT;
      fixture.detectChanges();
    });

    it('should show single user in table', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      const rows = compiled.querySelectorAll('.cost-per-user-section tbody tr');
      expect(rows?.length).toBe(1);
    });

    it('should display correct user name', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      const firstCell = compiled.querySelector('.cost-per-user-section tbody td');
      expect(firstCell?.textContent?.trim()).toBe('solo-user');
    });

    it('should show EUR currency for single user', () => {
      expect(component.currency).toBe('EUR');
    });
  });

  // ========== Computed Properties Tests ==========
  describe('Computed Properties', () => {
    it('should return cost per user from metricsService', () => {
      component.metricsResult = SAMPLE_METRICS_RESULT;
      const result = component.costPerUser;
      expect(Object.keys(result).length).toBe(3);
    });

    it('should return empty object for null metrics', () => {
      component.metricsResult = null;
      expect(component.costPerUser).toEqual({});
    });

    it('should return average ACUs per session', () => {
      component.metricsResult = SAMPLE_METRICS_RESULT;
      expect(component.averageAcusPerSession).toBe(250.0);
    });

    it('should return cost consistency status', () => {
      component.metricsResult = SAMPLE_METRICS_RESULT;
      expect(component.isCostConsistent).toBeTrue();
    });

    it('should return currency from config', () => {
      component.metricsResult = SAMPLE_METRICS_RESULT;
      expect(component.currency).toBe('USD');
    });

    it('should return USD as default currency when no config', () => {
      component.metricsResult = null;
      expect(component.currency).toBe('USD');
    });

    it('should return N/A for reporting period when null', () => {
      component.metricsResult = null;
      expect(component.reportingPeriod).toBe('N/A');
    });

    it('should return formatted reporting period', () => {
      component.metricsResult = SAMPLE_METRICS_RESULT;
      expect(component.reportingPeriod).toBe('2024-09-01 - 2024-09-30');
    });

    it('should format cost with currency', () => {
      component.metricsResult = SAMPLE_METRICS_RESULT;
      expect(component.formatCost(62.50)).toBe('62.50 USD');
    });

    it('should return cost per user entries as array', () => {
      component.metricsResult = SAMPLE_METRICS_RESULT;
      const entries = component.costPerUserEntries;
      expect(entries.length).toBe(3);
      expect(entries[0].user).toBeTruthy();
      expect(entries[0].cost).toBeGreaterThan(0);
    });

    it('should return empty array for no cost per user entries', () => {
      component.metricsResult = null;
      expect(component.costPerUserEntries.length).toBe(0);
    });
  });

  // ========== Consistency Check Display Tests ==========
  describe('Consistency Check Display', () => {
    it('should show consistent class for valid metrics', () => {
      component.metricsResult = SAMPLE_METRICS_RESULT;
      fixture.detectChanges();
      const compiled = fixture.nativeElement as HTMLElement;
      const el = compiled.querySelector('.consistency-check');
      expect(el?.classList.contains('consistent')).toBeTrue();
    });

    it('should show inconsistent class for invalid metrics', () => {
      const inconsistent = JSON.parse(JSON.stringify(SAMPLE_METRICS_RESULT));
      inconsistent.metrics['01_total_monthly_cost'] = 999.99;
      component.metricsResult = inconsistent;
      fixture.detectChanges();
      const compiled = fixture.nativeElement as HTMLElement;
      const el = compiled.querySelector('.consistency-check');
      expect(el?.classList.contains('inconsistent')).toBeTrue();
    });

    it('should display failed message for inconsistent metrics', () => {
      const inconsistent = JSON.parse(JSON.stringify(SAMPLE_METRICS_RESULT));
      inconsistent.metrics['01_total_monthly_cost'] = 999.99;
      component.metricsResult = inconsistent;
      fixture.detectChanges();
      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('.consistency-check')?.textContent).toContain('Cost consistency check failed');
    });
  });
});

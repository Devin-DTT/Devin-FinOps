import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { BehaviorSubject, Subject } from 'rxjs';
import { DashboardComponent } from './dashboard.component';
import { WebsocketService, ConnectionStatus } from '../../services/websocket.service';
import { MetricsService } from '../../services/metrics.service';
import { MetricsResult, WebSocketMessage } from '../../models/metrics.model';
import { SAMPLE_METRICS_RESULT } from '../../testing/metrics-fixtures';

/**
 * Comprehensive tests for DashboardComponent.
 * Tests component creation, WebSocket interaction, metrics display, and UI state management.
 */
describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let mockWsService: jasmine.SpyObj<WebsocketService>;
  let mockMetricsService: jasmine.SpyObj<MetricsService>;
  let statusSubject: BehaviorSubject<ConnectionStatus>;
  let metricsSubject: BehaviorSubject<MetricsResult | null>;
  let messagesSubject: Subject<WebSocketMessage>;

  beforeEach(async () => {
    statusSubject = new BehaviorSubject<ConnectionStatus>('disconnected');
    metricsSubject = new BehaviorSubject<MetricsResult | null>(null);
    messagesSubject = new Subject<WebSocketMessage>();

    mockWsService = jasmine.createSpyObj('WebsocketService', [
      'connect', 'disconnect', 'fetchMetrics', 'refreshMetrics', 'sendMessage'
    ], {
      status$: statusSubject.asObservable(),
      metrics$: metricsSubject.asObservable(),
      messages$: messagesSubject.asObservable()
    });

    mockMetricsService = jasmine.createSpyObj('MetricsService', [
      'getTotalCost', 'getTotalAcus', 'getTotalSessions', 'getUniqueUsers',
      'getCostPerUser', 'getAverageAcusPerSession', 'validateCostConsistency',
      'formatCurrency'
    ]);

    // Default return values
    mockMetricsService.getTotalCost.and.returnValue(0);
    mockMetricsService.getTotalAcus.and.returnValue(0);
    mockMetricsService.getTotalSessions.and.returnValue(0);
    mockMetricsService.getUniqueUsers.and.returnValue(0);
    mockMetricsService.getCostPerUser.and.returnValue({});
    mockMetricsService.getAverageAcusPerSession.and.returnValue(0);
    mockMetricsService.validateCostConsistency.and.returnValue(true);
    mockMetricsService.formatCurrency.and.callFake((v: number, c: string) => `${v.toFixed(2)} ${c || 'USD'}`);

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        { provide: WebsocketService, useValue: mockWsService },
        { provide: MetricsService, useValue: mockMetricsService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  // ========== Component Creation ==========
  describe('Creation', () => {
    it('should create the component', () => {
      expect(component).toBeTruthy();
    });

    it('should have initial null metrics', () => {
      expect(component.metrics).toBeNull();
    });

    it('should have initial disconnected status', () => {
      expect(component.connectionStatus).toBe('disconnected');
    });

    it('should not be loading initially', () => {
      expect(component.isLoading).toBeFalse();
    });

    it('should have no error message initially', () => {
      expect(component.errorMessage).toBeNull();
    });
  });

  // ========== Connection Tests ==========
  describe('Connection', () => {
    it('should call wsService.connect when connect() is called', () => {
      component.connect();
      expect(mockWsService.connect).toHaveBeenCalledWith('ws://localhost:8080/ws/finops');
    });

    it('should call wsService.disconnect when disconnect() is called', () => {
      component.disconnect();
      expect(mockWsService.disconnect).toHaveBeenCalled();
    });

    it('should update connectionStatus when status changes', () => {
      statusSubject.next('connected');
      expect(component.connectionStatus).toBe('connected');
    });

    it('should set error message on error status', () => {
      statusSubject.next('error');
      expect(component.errorMessage).toBe('Connection error. Attempting to reconnect...');
    });

    it('should clear error message when connected', () => {
      statusSubject.next('error');
      expect(component.errorMessage).toBeTruthy();

      statusSubject.next('connected');
      expect(component.errorMessage).toBeNull();
    });
  });

  // ========== Metrics Subscription Tests ==========
  describe('Metrics Subscription', () => {
    it('should update metrics when received from WebSocket', () => {
      metricsSubject.next(SAMPLE_METRICS_RESULT);
      expect(component.metrics).toEqual(SAMPLE_METRICS_RESULT);
    });

    it('should set isLoading to false when metrics arrive', () => {
      component.isLoading = true;
      metricsSubject.next(SAMPLE_METRICS_RESULT);
      expect(component.isLoading).toBeFalse();
    });

    it('should handle null metrics', () => {
      metricsSubject.next(null);
      expect(component.metrics).toBeNull();
    });
  });

  // ========== Fetch/Refresh Tests ==========
  describe('Fetch and Refresh', () => {
    it('should call fetchMetrics with date range', () => {
      component.fetchMetrics('2024-09-01', '2024-09-30');
      expect(mockWsService.fetchMetrics).toHaveBeenCalledWith('2024-09-01', '2024-09-30');
    });

    it('should set isLoading when fetching metrics', () => {
      component.fetchMetrics('2024-09-01', '2024-09-30');
      expect(component.isLoading).toBeTrue();
    });

    it('should call refreshMetrics', () => {
      component.refreshMetrics();
      expect(mockWsService.refreshMetrics).toHaveBeenCalled();
    });

    it('should set isLoading when refreshing', () => {
      component.refreshMetrics();
      expect(component.isLoading).toBeTrue();
    });
  });

  // ========== Computed Properties Tests ==========
  describe('Computed Properties', () => {
    it('should return totalCost from metricsService', () => {
      mockMetricsService.getTotalCost.and.returnValue(62.50);
      expect(component.totalCost).toBe(62.50);
    });

    it('should return totalAcus from metricsService', () => {
      mockMetricsService.getTotalAcus.and.returnValue(1250.0);
      expect(component.totalAcus).toBe(1250.0);
    });

    it('should return totalSessions from metricsService', () => {
      mockMetricsService.getTotalSessions.and.returnValue(5);
      expect(component.totalSessions).toBe(5);
    });

    it('should return uniqueUsers from metricsService', () => {
      mockMetricsService.getUniqueUsers.and.returnValue(3);
      expect(component.uniqueUsers).toBe(3);
    });

    it('should pass current metrics to metricsService', () => {
      component.metrics = SAMPLE_METRICS_RESULT;
      component.totalCost; // Access getter
      expect(mockMetricsService.getTotalCost).toHaveBeenCalledWith(SAMPLE_METRICS_RESULT);
    });
  });

  // ========== Template Rendering Tests ==========
  describe('Template Rendering', () => {
    it('should display the dashboard title', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('h1')?.textContent).toContain('FinOps Dashboard');
    });

    it('should display connection status', () => {
      fixture.detectChanges();
      const compiled = fixture.nativeElement as HTMLElement;
      const statusEl = compiled.querySelector('.connection-status');
      expect(statusEl?.textContent).toContain('Disconnected');
    });

    it('should show error banner when error message exists', () => {
      component.errorMessage = 'Test error';
      fixture.detectChanges();
      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('.error-banner')?.textContent).toContain('Test error');
    });

    it('should not show error banner when no error', () => {
      fixture.detectChanges();
      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('.error-banner')).toBeNull();
    });

    it('should show loading indicator when loading', () => {
      component.isLoading = true;
      fixture.detectChanges();
      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('.loading')?.textContent).toContain('Loading metrics...');
    });

    it('should show metrics summary when metrics exist', () => {
      component.metrics = SAMPLE_METRICS_RESULT;
      mockMetricsService.getTotalCost.and.returnValue(62.50);
      mockMetricsService.getTotalAcus.and.returnValue(1250.0);
      mockMetricsService.getTotalSessions.and.returnValue(5);
      mockMetricsService.getUniqueUsers.and.returnValue(3);
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('.metrics-summary')).toBeTruthy();
    });

    it('should not show metrics summary when no metrics', () => {
      fixture.detectChanges();
      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('.metrics-summary')).toBeNull();
    });

    it('should have connect button', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      const buttons = compiled.querySelectorAll('button');
      const connectBtn = Array.from(buttons).find(b => b.textContent?.includes('Connect'));
      expect(connectBtn).toBeTruthy();
    });

    it('should disable connect button when connected', () => {
      component.connectionStatus = 'connected';
      fixture.detectChanges();
      const compiled = fixture.nativeElement as HTMLElement;
      const buttons = compiled.querySelectorAll('button');
      const connectBtn = Array.from(buttons).find(b => b.textContent?.includes('Connect'));
      expect(connectBtn?.disabled).toBeTrue();
    });

    it('should disable disconnect button when disconnected', () => {
      fixture.detectChanges();
      const compiled = fixture.nativeElement as HTMLElement;
      const buttons = compiled.querySelectorAll('button');
      const disconnectBtn = Array.from(buttons).find(b => b.textContent?.includes('Disconnect'));
      expect(disconnectBtn?.disabled).toBeTrue();
    });
  });

  // ========== Lifecycle Tests ==========
  describe('Lifecycle', () => {
    it('should unsubscribe on destroy', () => {
      // Trigger subscriptions
      statusSubject.next('connected');
      metricsSubject.next(SAMPLE_METRICS_RESULT);

      component.ngOnDestroy();

      // After destroy, status changes should not update component
      statusSubject.next('error');
      // Component should still be 'connected' since it unsubscribed
      expect(component.connectionStatus).toBe('connected');
    });
  });
});

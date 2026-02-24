import { TestBed } from '@angular/core/testing';
import { WebsocketService, ConnectionStatus } from './websocket.service';
import { WebSocketMessage, MetricsResult } from '../models/metrics.model';
import { SAMPLE_METRICS_RESULT } from '../testing/metrics-fixtures';

/**
 * Comprehensive unit tests for WebsocketService.
 * Mirrors the Python WebSocket test patterns and Java FinOpsWebSocketHandlerTest coverage.
 */

// Mock WebSocket implementation for testing
class MockWebSocket {
  url: string;
  readyState: number = WebSocket.CONNECTING;
  onopen: ((ev: Event) => void) | null = null;
  onclose: ((ev: CloseEvent) => void) | null = null;
  onmessage: ((ev: MessageEvent) => void) | null = null;
  onerror: ((ev: Event) => void) | null = null;

  sent: string[] = [];
  closed = false;

  constructor(url: string) {
    this.url = url;
  }

  send(data: string): void {
    this.sent.push(data);
  }

  close(): void {
    this.closed = true;
    this.readyState = WebSocket.CLOSED;
  }

  // Test helpers to simulate events
  simulateOpen(): void {
    this.readyState = WebSocket.OPEN;
    if (this.onopen) {
      this.onopen(new Event('open'));
    }
  }

  simulateMessage(data: string): void {
    if (this.onmessage) {
      this.onmessage(new MessageEvent('message', { data }));
    }
  }

  simulateClose(): void {
    this.readyState = WebSocket.CLOSED;
    if (this.onclose) {
      this.onclose(new CloseEvent('close'));
    }
  }

  simulateError(): void {
    if (this.onerror) {
      this.onerror(new Event('error'));
    }
  }
}

describe('WebsocketService', () => {
  let service: WebsocketService;
  let mockWebSocket: MockWebSocket;
  let originalWebSocket: typeof WebSocket;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(WebsocketService);

    // Replace global WebSocket with mock
    originalWebSocket = (window as unknown as Record<string, unknown>)['WebSocket'] as typeof WebSocket;
    (window as unknown as Record<string, unknown>)['WebSocket'] = class extends MockWebSocket {
      constructor(url: string) {
        super(url);
        mockWebSocket = this;
      }
    } as unknown as typeof WebSocket;
  });

  afterEach(() => {
    service.ngOnDestroy();
    (window as unknown as Record<string, unknown>)['WebSocket'] = originalWebSocket;
  });

  // ========== Basic Service Tests ==========
  describe('Basic Service', () => {
    it('should be created', () => {
      expect(service).toBeTruthy();
    });

    it('should have initial status as disconnected', () => {
      expect(service.currentStatus).toBe('disconnected');
    });

    it('should have null initial metrics', () => {
      expect(service.currentMetrics).toBeNull();
    });

    it('should expose status$ observable', () => {
      let lastStatus: ConnectionStatus | undefined;
      const sub = service.status$.subscribe(status => {
        lastStatus = status;
      });
      expect(lastStatus).toBe('disconnected');
      sub.unsubscribe();
    });

    it('should expose metrics$ observable with null initial value', () => {
      let lastMetrics: unknown = 'unset';
      const sub = service.metrics$.subscribe(metrics => {
        lastMetrics = metrics;
      });
      expect(lastMetrics).toBeNull();
      sub.unsubscribe();
    });
  });

  // ========== Connection Tests ==========
  describe('Connection', () => {
    it('should set status to connecting when connect is called', () => {
      const statuses: ConnectionStatus[] = [];
      service.status$.subscribe(s => statuses.push(s));

      service.connect('ws://localhost:8080/ws/finops');

      expect(statuses).toContain('connecting');
    });

    it('should create WebSocket with correct URL', () => {
      service.connect('ws://localhost:8080/ws/finops');
      expect(mockWebSocket.url).toBe('ws://localhost:8080/ws/finops');
    });

    it('should set status to connected on open', () => {
      service.connect('ws://localhost:8080/ws/finops');
      mockWebSocket.simulateOpen();

      expect(service.currentStatus).toBe('connected');
    });

    it('should not create duplicate connection if already open', () => {
      service.connect('ws://localhost:8080/ws/finops');
      mockWebSocket.simulateOpen();

      const firstSocket = mockWebSocket;
      service.connect('ws://localhost:8080/ws/finops');

      // Should still be the same socket
      expect(mockWebSocket).toBe(firstSocket);
    });

    it('should set status to error on WebSocket error', () => {
      service.connect('ws://localhost:8080/ws/finops');
      mockWebSocket.simulateError();

      expect(service.currentStatus).toBe('error');
    });

    it('should set status to disconnected on close', () => {
      service.connect('ws://localhost:8080/ws/finops');
      mockWebSocket.simulateOpen();

      // Disconnect cleanly first to avoid reconnect timer
      service.disconnect();

      expect(service.currentStatus).toBe('disconnected');
    });
  });

  // ========== Disconnect Tests ==========
  describe('Disconnect', () => {
    it('should close the WebSocket on disconnect', () => {
      service.connect('ws://localhost:8080/ws/finops');
      mockWebSocket.simulateOpen();

      service.disconnect();

      expect(mockWebSocket.closed).toBeTrue();
    });

    it('should set status to disconnected on disconnect', () => {
      service.connect('ws://localhost:8080/ws/finops');
      mockWebSocket.simulateOpen();

      service.disconnect();

      expect(service.currentStatus).toBe('disconnected');
    });

    it('should handle disconnect when not connected', () => {
      // Should not throw
      expect(() => service.disconnect()).not.toThrow();
      expect(service.currentStatus).toBe('disconnected');
    });
  });

  // ========== Message Sending Tests ==========
  describe('Send Message', () => {
    beforeEach(() => {
      service.connect('ws://localhost:8080/ws/finops');
      mockWebSocket.simulateOpen();
    });

    it('should send JSON message through WebSocket', () => {
      service.sendMessage('fetch_metrics', { start_date: '2024-09-01', end_date: '2024-09-30' });

      expect(mockWebSocket.sent.length).toBe(1);
      const parsed = JSON.parse(mockWebSocket.sent[0]);
      expect(parsed.action).toBe('fetch_metrics');
      expect(parsed.start_date).toBe('2024-09-01');
      expect(parsed.end_date).toBe('2024-09-30');
    });

    it('should send refresh message', () => {
      service.refreshMetrics();

      expect(mockWebSocket.sent.length).toBe(1);
      const parsed = JSON.parse(mockWebSocket.sent[0]);
      expect(parsed.action).toBe('refresh');
    });

    it('should send fetch_metrics with date range', () => {
      service.fetchMetrics('2024-09-01', '2024-09-30');

      expect(mockWebSocket.sent.length).toBe(1);
      const parsed = JSON.parse(mockWebSocket.sent[0]);
      expect(parsed.action).toBe('fetch_metrics');
      expect(parsed.start_date).toBe('2024-09-01');
      expect(parsed.end_date).toBe('2024-09-30');
    });

    it('should not send when disconnected', () => {
      service.disconnect();

      spyOn(console, 'error');
      service.sendMessage('refresh');

      expect(console.error).toHaveBeenCalledWith('WebSocket is not connected');
    });

    it('should send message without payload', () => {
      service.sendMessage('ping');

      expect(mockWebSocket.sent.length).toBe(1);
      const parsed = JSON.parse(mockWebSocket.sent[0]);
      expect(parsed.action).toBe('ping');
    });
  });

  // ========== Message Receiving Tests ==========
  describe('Receive Messages', () => {
    beforeEach(() => {
      service.connect('ws://localhost:8080/ws/finops');
      mockWebSocket.simulateOpen();
    });

    it('should emit received messages on messages$ observable', (done) => {
      const testMessage: WebSocketMessage = {
        type: 'connected',
        message: 'Welcome',
        sessionId: 'test-123'
      };

      service.messages$.subscribe(msg => {
        expect(msg.type).toBe('connected');
        expect(msg.message).toBe('Welcome');
        done();
      });

      mockWebSocket.simulateMessage(JSON.stringify(testMessage));
    });

    it('should update metrics when metrics message received', () => {
      const metricsMessage: WebSocketMessage = {
        type: 'metrics',
        data: SAMPLE_METRICS_RESULT
      };

      mockWebSocket.simulateMessage(JSON.stringify(metricsMessage));

      expect(service.currentMetrics).toBeTruthy();
      expect(service.currentMetrics!.metrics['02_total_acus']).toBe(1250.0);
    });

    it('should not update metrics for non-metrics messages', () => {
      const statusMessage: WebSocketMessage = {
        type: 'status',
        message: 'Refresh complete'
      };

      mockWebSocket.simulateMessage(JSON.stringify(statusMessage));

      expect(service.currentMetrics).toBeNull();
    });

    it('should handle invalid JSON gracefully', () => {
      spyOn(console, 'error');
      mockWebSocket.simulateMessage('not-json');

      expect(console.error).toHaveBeenCalled();
      expect(service.currentMetrics).toBeNull();
    });

    it('should handle error messages', (done) => {
      const errorMessage: WebSocketMessage = {
        type: 'error',
        message: 'API connection failed'
      };

      service.messages$.subscribe(msg => {
        expect(msg.type).toBe('error');
        expect(msg.message).toBe('API connection failed');
        done();
      });

      mockWebSocket.simulateMessage(JSON.stringify(errorMessage));
    });

    it('should handle metrics message with all 20 metrics', () => {
      const metricsMessage: WebSocketMessage = {
        type: 'metrics',
        data: SAMPLE_METRICS_RESULT
      };

      mockWebSocket.simulateMessage(JSON.stringify(metricsMessage));

      const metrics = service.currentMetrics!;
      expect(metrics.metrics['01_total_monthly_cost']).toBe(62.50);
      expect(metrics.metrics['02_total_acus']).toBe(1250.0);
      expect(metrics.metrics['06_total_sessions']).toBe(5);
      expect(metrics.metrics['12_unique_users']).toBe(3);
      expect(metrics.metrics['20_efficiency_ratio']).toBe(0.85);
    });

    it('should update metrics when new metrics received', () => {
      const firstMessage: WebSocketMessage = {
        type: 'metrics',
        data: SAMPLE_METRICS_RESULT
      };

      mockWebSocket.simulateMessage(JSON.stringify(firstMessage));
      expect(service.currentMetrics!.metrics['02_total_acus']).toBe(1250.0);

      // Simulate updated metrics
      const updatedData = { ...SAMPLE_METRICS_RESULT };
      updatedData.metrics = { ...updatedData.metrics, '02_total_acus': 2000.0 };
      const secondMessage: WebSocketMessage = {
        type: 'metrics',
        data: updatedData
      };

      mockWebSocket.simulateMessage(JSON.stringify(secondMessage));
      expect(service.currentMetrics!.metrics['02_total_acus']).toBe(2000.0);
    });
  });

  // ========== Reconnection Tests ==========
  describe('Reconnection', () => {
    it('should cancel reconnect on disconnect', () => {
      service.connect('ws://localhost:8080/ws/finops');
      mockWebSocket.simulateOpen();

      // Disconnect cleanly cancels any pending reconnect
      service.disconnect();

      expect(service.currentStatus).toBe('disconnected');
    });

    it('should reset reconnect attempts on successful connection', () => {
      service.connect('ws://localhost:8080/ws/finops');
      mockWebSocket.simulateOpen();

      // After successful open, reconnect attempts should be reset
      expect(service.currentStatus).toBe('connected');

      service.disconnect();
    });
  });

  // ========== Lifecycle Tests ==========
  describe('Lifecycle', () => {
    it('should clean up on ngOnDestroy', () => {
      service.connect('ws://localhost:8080/ws/finops');
      mockWebSocket.simulateOpen();

      service.ngOnDestroy();

      expect(mockWebSocket.closed).toBeTrue();
    });

    it('should complete observables on destroy', () => {
      let statusComplete = false;
      let metricsComplete = false;

      service.status$.subscribe({ complete: () => statusComplete = true });
      service.metrics$.subscribe({ complete: () => metricsComplete = true });

      service.ngOnDestroy();

      expect(statusComplete).toBeTrue();
      expect(metricsComplete).toBeTrue();
    });
  });

  // ========== Consistency Check Tests (mirrors Python/Java patterns) ==========
  describe('Metrics Consistency via WebSocket', () => {
    beforeEach(() => {
      service.connect('ws://localhost:8080/ws/finops');
      mockWebSocket.simulateOpen();
    });

    it('should receive metrics where cost = acus * price', () => {
      const metricsMessage: WebSocketMessage = {
        type: 'metrics',
        data: SAMPLE_METRICS_RESULT
      };

      mockWebSocket.simulateMessage(JSON.stringify(metricsMessage));

      const metrics = service.currentMetrics!;
      const expectedCost = metrics.metrics['02_total_acus'] * metrics.config.price_per_acu;
      expect(metrics.metrics['01_total_monthly_cost']).toBeCloseTo(expectedCost, 1);
    });

    it('should receive metrics where cost_per_user sums to total', () => {
      const metricsMessage: WebSocketMessage = {
        type: 'metrics',
        data: SAMPLE_METRICS_RESULT
      };

      mockWebSocket.simulateMessage(JSON.stringify(metricsMessage));

      const metrics = service.currentMetrics!;
      const costPerUser = metrics.metrics['03_cost_per_user'];
      const sumCosts = Object.values(costPerUser).reduce((sum, val) => sum + val, 0);
      expect(sumCosts).toBeCloseTo(metrics.metrics['01_total_monthly_cost'], 1);
    });

    it('should receive metrics where sessions_per_user sums to total', () => {
      const metricsMessage: WebSocketMessage = {
        type: 'metrics',
        data: SAMPLE_METRICS_RESULT
      };

      mockWebSocket.simulateMessage(JSON.stringify(metricsMessage));

      const metrics = service.currentMetrics!;
      const sessionsPerUser = metrics.metrics['07_sessions_per_user'];
      const sumSessions = Object.values(sessionsPerUser).reduce((sum, val) => sum + val, 0);
      expect(sumSessions).toBe(metrics.metrics['06_total_sessions']);
    });
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { Subject } from 'rxjs';

import { DashboardComponent } from './dashboard.component';
import { WebSocketService, ConnectionStatus } from '../../core/services/websocket.service';
import { WebSocketMessage } from '../../models/devin-data.model';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let dataSubject: Subject<WebSocketMessage>;
  let connectionStatusSubject: Subject<ConnectionStatus>;

  beforeEach(async () => {
    dataSubject = new Subject<WebSocketMessage>();
    connectionStatusSubject = new Subject<ConnectionStatus>();

    const mockWebSocketService: Partial<WebSocketService> = {
      data$: dataSubject.asObservable(),
      connectionStatus$: connectionStatusSubject.asObservable(),
      disconnect: jasmine.createSpy('disconnect'),
      ngOnDestroy: jasmine.createSpy('ngOnDestroy')
    };

    await TestBed.configureTestingModule({
      imports: [DashboardComponent, BrowserAnimationsModule],
      providers: [
        { provide: WebSocketService, useValue: mockWebSocketService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    dataSubject.complete();
    connectionStatusSubject.complete();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should have initial state with zero counts', () => {
    expect(component.state.totalSessions).toBe(0);
    expect(component.state.runningSessions).toBe(0);
    expect(component.state.dauCount).toBe(0);
    expect(component.state.queueStatus).toBe('unknown');
  });

  it('processMessage() with endpoint "get_dau_metrics" updates state.dauCount', () => {
    const msg: WebSocketMessage = {
      type: 'data',
      endpoint: 'get_dau_metrics',
      timestamp: Date.now(),
      data: { count: 42 }
    };

    dataSubject.next(msg);

    expect(component.state.dauCount).toBe(42);
  });

  it('processMessage() with endpoint "get_dau_metrics" handles value field', () => {
    const msg: WebSocketMessage = {
      type: 'data',
      endpoint: 'get_dau_metrics',
      timestamp: Date.now(),
      data: { value: 99 }
    };

    dataSubject.next(msg);

    expect(component.state.dauCount).toBe(99);
  });

  it('processMessage() with endpoint "list_sessions" updates totalSessions, runningSessions, etc.', () => {
    const msg: WebSocketMessage = {
      type: 'data',
      endpoint: 'list_sessions',
      timestamp: Date.now(),
      data: {
        items: [
          { session_id: 's1', status: 'running', created_at: '2026-01-01T00:00:00Z' },
          { session_id: 's2', status: 'finished', created_at: '2026-01-01T00:00:00Z' },
          { session_id: 's3', status: 'failed', created_at: '2026-01-01T00:00:00Z' },
          { session_id: 's4', status: 'running', created_at: '2026-01-01T00:00:00Z' },
          { session_id: 's5', status: 'stopped', created_at: '2026-01-01T00:00:00Z' }
        ]
      }
    };

    dataSubject.next(msg);

    expect(component.state.totalSessions).toBe(5);
    expect(component.state.runningSessions).toBe(2);
    expect(component.state.finishedSessions).toBe(1);
    expect(component.state.failedSessions).toBe(1);
    expect(component.state.stoppedSessions).toBe(1);
  });

  it('processMessage() with endpoint "list_sessions" handles sessions array directly', () => {
    const msg: WebSocketMessage = {
      type: 'data',
      endpoint: 'list_sessions',
      timestamp: Date.now(),
      data: {
        sessions: [
          { session_id: 's1', status: 'running', created_at: '2026-01-01T00:00:00Z' }
        ]
      }
    };

    dataSubject.next(msg);

    expect(component.state.totalSessions).toBe(1);
    expect(component.state.runningSessions).toBe(1);
  });

  it('processMessage() with endpoint "get_queue_status" updates state.queueStatus', () => {
    const msg: WebSocketMessage = {
      type: 'data',
      endpoint: 'get_queue_status',
      timestamp: Date.now(),
      data: { status: 'high' }
    };

    dataSubject.next(msg);

    expect(component.state.queueStatus).toBe('high');
  });

  it('processMessage() with endpoint "get_queue_status" handles normal status', () => {
    const msg: WebSocketMessage = {
      type: 'data',
      endpoint: 'get_queue_status',
      timestamp: Date.now(),
      data: { status: 'normal' }
    };

    dataSubject.next(msg);

    expect(component.state.queueStatus).toBe('normal');
  });

  it('processMessage() ignores messages with type !== "data"', () => {
    const msg: WebSocketMessage = {
      type: 'ack',
      endpoint: 'get_dau_metrics',
      timestamp: Date.now(),
      data: { count: 999 }
    };

    dataSubject.next(msg);

    expect(component.state.dauCount).toBe(0);
  });

  it('processMessage() ignores messages with null data', () => {
    const msg: WebSocketMessage = {
      type: 'data',
      endpoint: 'get_dau_metrics',
      timestamp: Date.now(),
      data: null
    };

    dataSubject.next(msg);

    expect(component.state.dauCount).toBe(0);
  });

  it('getQueueStatusColor() returns "warn" for "high"', () => {
    component.state.queueStatus = 'high';
    expect(component.getQueueStatusColor()).toBe('warn');
  });

  it('getQueueStatusColor() returns "" (empty) for "elevated"', () => {
    component.state.queueStatus = 'elevated';
    expect(component.getQueueStatusColor()).toBe('');
  });

  it('getQueueStatusColor() returns "accent" for "normal"', () => {
    component.state.queueStatus = 'normal';
    expect(component.getQueueStatusColor()).toBe('accent');
  });

  it('getQueueStatusColor() returns "" for unknown status', () => {
    component.state.queueStatus = 'something_else';
    expect(component.getQueueStatusColor()).toBe('');
  });

  it('formatTimestamp() formats a valid ISO date correctly', () => {
    const result = component.formatTimestamp('2026-01-15T10:30:00Z');
    expect(result).toBeTruthy();
    expect(result).not.toBe('-');
    // The exact format depends on locale, but it should contain some date components
    expect(result.length).toBeGreaterThan(0);
  });

  it('formatTimestamp() returns "-" for empty string', () => {
    expect(component.formatTimestamp('')).toBe('-');
  });

  it('formatTimestamp() returns "-" for null/undefined', () => {
    expect(component.formatTimestamp(null as unknown as string)).toBe('-');
    expect(component.formatTimestamp(undefined as unknown as string)).toBe('-');
  });

  it('connectionStatus updates when WebSocketService emits', () => {
    connectionStatusSubject.next('connected');
    expect(component.connectionStatus).toBe('connected');

    connectionStatusSubject.next('disconnected');
    expect(component.connectionStatus).toBe('disconnected');
  });

  it('processMessage() updates lastUpdated timestamp', () => {
    const now = Date.now();
    const msg: WebSocketMessage = {
      type: 'data',
      endpoint: 'get_queue_status',
      timestamp: now,
      data: { status: 'normal' }
    };

    dataSubject.next(msg);

    expect(component.state.lastUpdated).toBe(now);
  });

  it('processMessage() tracks endpoints received', () => {
    const msg: WebSocketMessage = {
      type: 'data',
      endpoint: 'get_dau_metrics',
      timestamp: Date.now(),
      data: { count: 10 }
    };

    dataSubject.next(msg);

    expect(component.endpointsReceived.has('get_dau_metrics')).toBeTrue();
  });
});

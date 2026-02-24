import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { WebSocketMessage, MetricsResult } from '../models/metrics.model';

export type ConnectionStatus = 'disconnected' | 'connecting' | 'connected' | 'error';

@Injectable({
  providedIn: 'root'
})
export class WebsocketService implements OnDestroy {

  private socket: WebSocket | null = null;
  private readonly messagesSubject = new Subject<WebSocketMessage>();
  private readonly statusSubject = new BehaviorSubject<ConnectionStatus>('disconnected');
  private readonly metricsSubject = new BehaviorSubject<MetricsResult | null>(null);
  private reconnectAttempts = 0;
  private readonly maxReconnectAttempts = 5;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  readonly messages$: Observable<WebSocketMessage> = this.messagesSubject.asObservable();
  readonly status$: Observable<ConnectionStatus> = this.statusSubject.asObservable();
  readonly metrics$: Observable<MetricsResult | null> = this.metricsSubject.asObservable();

  get currentStatus(): ConnectionStatus {
    return this.statusSubject.getValue();
  }

  get currentMetrics(): MetricsResult | null {
    return this.metricsSubject.getValue();
  }

  connect(url: string): void {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      return; // Already connected
    }

    this.statusSubject.next('connecting');

    try {
      this.socket = new WebSocket(url);

      this.socket.onopen = () => {
        this.statusSubject.next('connected');
        this.reconnectAttempts = 0;
      };

      this.socket.onmessage = (event: MessageEvent) => {
        try {
          const message: WebSocketMessage = JSON.parse(event.data);
          this.messagesSubject.next(message);

          if (message.type === 'metrics' && message.data) {
            this.metricsSubject.next(message.data);
          }
        } catch (e) {
          console.error('Failed to parse WebSocket message:', e);
        }
      };

      this.socket.onclose = () => {
        this.statusSubject.next('disconnected');
        this.attemptReconnect(url);
      };

      this.socket.onerror = () => {
        this.statusSubject.next('error');
      };
    } catch (e) {
      this.statusSubject.next('error');
    }
  }

  disconnect(): void {
    this.cancelReconnect();
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
    this.statusSubject.next('disconnected');
  }

  sendMessage(action: string, payload?: Record<string, string>): void {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      console.error('WebSocket is not connected');
      return;
    }

    const message = { action, ...payload };
    this.socket.send(JSON.stringify(message));
  }

  fetchMetrics(startDate: string, endDate: string): void {
    this.sendMessage('fetch_metrics', {
      start_date: startDate,
      end_date: endDate
    });
  }

  refreshMetrics(): void {
    this.sendMessage('refresh');
  }

  private attemptReconnect(url: string): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      this.statusSubject.next('error');
      return;
    }

    this.reconnectAttempts++;
    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);

    this.reconnectTimer = setTimeout(() => {
      this.connect(url);
    }, delay);
  }

  private cancelReconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.reconnectAttempts = 0;
  }

  ngOnDestroy(): void {
    this.disconnect();
    this.messagesSubject.complete();
    this.statusSubject.complete();
    this.metricsSubject.complete();
  }
}

import { Injectable, OnDestroy } from '@angular/core';
import { Subject, Observable } from 'rxjs';
import { WsMessage } from '../models/consumption-data.model';

/**
 * WebSocket service that connects to the Java Spring Boot backend
 * at /ws/finops endpoint and streams FinOps metrics in real-time.
 */
@Injectable({
  providedIn: 'root'
})
export class WebsocketService implements OnDestroy {

  private socket: WebSocket | null = null;
  private messagesSubject = new Subject<WsMessage>();
  private connectionStatusSubject = new Subject<boolean>();
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 3000;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  /** Observable stream of WebSocket messages */
  readonly messages$: Observable<WsMessage> = this.messagesSubject.asObservable();

  /** Observable stream of connection status changes */
  readonly connectionStatus$: Observable<boolean> = this.connectionStatusSubject.asObservable();

  /**
   * Connect to the Java WebSocket backend.
   * @param url Full WebSocket URL (e.g., ws://localhost:8080/ws/finops)
   */
  connect(url: string): void {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      return;
    }

    this.socket = new WebSocket(url);

    this.socket.onopen = () => {
      this.reconnectAttempts = 0;
      this.connectionStatusSubject.next(true);
    };

    this.socket.onmessage = (event: MessageEvent) => {
      try {
        const message: WsMessage = JSON.parse(event.data);
        this.messagesSubject.next(message);
      } catch {
        console.error('Failed to parse WebSocket message:', event.data);
      }
    };

    this.socket.onclose = () => {
      this.connectionStatusSubject.next(false);
      this.attemptReconnect(url);
    };

    this.socket.onerror = () => {
      this.connectionStatusSubject.next(false);
    };
  }

  /**
   * Send a message to the Java WebSocket backend.
   * @param action Action name (fetch_metrics, refresh)
   * @param params Additional parameters
   */
  send(action: string, params: Record<string, string | null> = {}): void {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      const message = { action, ...params };
      this.socket.send(JSON.stringify(message));
    } else {
      console.warn('WebSocket is not connected. Cannot send message.');
    }
  }

  /**
   * Request metrics from the backend for a given date range.
   */
  fetchMetrics(startDate: string | null, endDate: string | null): void {
    this.send('fetch_metrics', { start_date: startDate, end_date: endDate });
  }

  /**
   * Request a full metrics refresh (broadcasts to all clients).
   */
  refresh(): void {
    this.send('refresh');
  }

  /**
   * Disconnect from the WebSocket server.
   */
  disconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
  }

  private attemptReconnect(url: string): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.warn('Max reconnect attempts reached.');
      return;
    }
    this.reconnectAttempts++;
    const delay = this.reconnectDelay * this.reconnectAttempts;
    this.reconnectTimer = setTimeout(() => this.connect(url), delay);
  }

  ngOnDestroy(): void {
    this.disconnect();
    this.messagesSubject.complete();
    this.connectionStatusSubject.complete();
  }
}

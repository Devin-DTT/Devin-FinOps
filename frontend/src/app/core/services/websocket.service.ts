import { Injectable, OnDestroy } from '@angular/core';
import { Observable, Subject, timer, EMPTY } from 'rxjs';
import { switchMap, catchError } from 'rxjs/operators';
import { webSocket, WebSocketSubject } from 'rxjs/webSocket';
import { WebSocketMessage } from '../../models/devin-data.model';
import { environment } from '../../../environments/environment';

export type ConnectionStatus = 'connected' | 'disconnected' | 'connecting';

@Injectable({
  providedIn: 'root'
})
export class WebSocketService implements OnDestroy {
  private socket$: WebSocketSubject<WebSocketMessage> | null = null;
  private dataSubject$ = new Subject<WebSocketMessage>();
  private connectionStatusSubject$ = new Subject<ConnectionStatus>();
  private reconnectAttempts = 0;
  private readonly maxReconnectAttempts = 10;
  private readonly initialReconnectDelay = 1000;
  private isDestroyed = false;

  readonly data$: Observable<WebSocketMessage> = this.dataSubject$.asObservable();
  readonly connectionStatus$: Observable<ConnectionStatus> = this.connectionStatusSubject$.asObservable();

  constructor() {
    this.connect();
  }

  private connect(): void {
    if (this.isDestroyed) {
      return;
    }

    this.connectionStatusSubject$.next('connecting');

    this.socket$ = webSocket<WebSocketMessage>({
      url: environment.wsUrl,
      openObserver: {
        next: () => {
          this.reconnectAttempts = 0;
          this.connectionStatusSubject$.next('connected');
        }
      },
      closeObserver: {
        next: () => {
          this.connectionStatusSubject$.next('disconnected');
          this.reconnect();
        }
      }
    });

    this.socket$.pipe(
      catchError((error) => {
        console.error('WebSocket error:', error);
        this.connectionStatusSubject$.next('disconnected');
        this.reconnect();
        return EMPTY;
      })
    ).subscribe({
      next: (msg: WebSocketMessage) => {
        this.dataSubject$.next(msg);
      },
      error: (error) => {
        console.error('WebSocket subscription error:', error);
        this.connectionStatusSubject$.next('disconnected');
        this.reconnect();
      }
    });
  }

  private reconnect(): void {
    if (this.isDestroyed || this.reconnectAttempts >= this.maxReconnectAttempts) {
      return;
    }

    const delay = this.initialReconnectDelay * Math.pow(2, this.reconnectAttempts);
    this.reconnectAttempts++;

    console.log(`Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`);

    timer(delay).pipe(
      switchMap(() => {
        this.connect();
        return EMPTY;
      })
    ).subscribe();
  }

  disconnect(): void {
    if (this.socket$) {
      this.socket$.complete();
      this.socket$ = null;
    }
  }

  ngOnDestroy(): void {
    this.isDestroyed = true;
    this.disconnect();
    this.dataSubject$.complete();
    this.connectionStatusSubject$.complete();
  }
}

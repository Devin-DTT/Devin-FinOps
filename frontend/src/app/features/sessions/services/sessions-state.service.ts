import { Injectable, signal, computed } from '@angular/core';
import { DevinSession, SessionsResponse } from '../models/session.model';
import { WebSocketMessage } from '../../../models/devin-data.model';

@Injectable({ providedIn: 'root' })
export class SessionsStateService {
  // Signals
  sessions = signal<DevinSession[]>([]);
  totalSessions = signal(0);
  runningSessions = signal(0);
  finishedSessions = signal(0);
  failedSessions = signal(0);
  stoppedSessions = signal(0);
  lastUpdated = signal(0);

  // Computed signals
  sessionSuccessRate = computed(() =>
    this.totalSessions() > 0
      ? (this.finishedSessions() / this.totalSessions()) * 100
      : 0
  );

  wasteToOutcomeRatio = computed(() =>
    this.finishedSessions() > 0
      ? (this.failedSessions() + this.stoppedSessions()) / this.finishedSessions()
      : 0
  );

  handleMessage(msg: WebSocketMessage): void {
    const data = msg.data as Record<string, unknown>;
    this.lastUpdated.set(msg.timestamp);
    this.handleSessions(data);
  }

  private handleSessions(data: Record<string, unknown>): void {
    let sessionList: DevinSession[];
    if (Array.isArray(data)) {
      sessionList = data as DevinSession[];
    } else if (Array.isArray(data['items'])) {
      sessionList = data['items'] as DevinSession[];
    } else {
      const resp = data as unknown as SessionsResponse;
      sessionList = Array.isArray(resp.sessions) ? resp.sessions : [];
    }

    this.sessions.set(sessionList);
    this.totalSessions.set(sessionList.length);
    this.runningSessions.set(sessionList.filter(s => s.status === 'running').length);
    this.finishedSessions.set(sessionList.filter(s => s.status === 'finished').length);
    this.failedSessions.set(sessionList.filter(s => s.status === 'failed').length);
    this.stoppedSessions.set(sessionList.filter(s => s.status === 'stopped').length);
  }
}

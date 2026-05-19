import { Injectable, signal, computed } from '@angular/core';
import { DevinSession, SessionsResponse } from '../models/session.model';
import { WebSocketMessage } from '../../../models/devin-data.model';

@Injectable({ providedIn: 'root' })
export class SessionsStateService {
  // Signals
  sessions = signal<DevinSession[]>([]);
  totalSessions = signal(0);
  runningSessions = signal(0);
  errorSessions = signal(0);
  suspendedSessions = signal(0);
  suspendedByInactivity = signal(0);
  suspendedByUser = signal(0);
  lastUpdated = signal(0);

  // Unique user IDs from sessions this month
  activeUserIds = computed(() => {
    const now = new Date();
    const monthStart = new Date(now.getFullYear(), now.getMonth(), 1).getTime() / 1000;
    const ids = new Set<string>();
    for (const s of this.sessions()) {
      const ts = typeof s.created_at === 'number' ? s.created_at : parseInt(s.created_at as string, 10);
      if (ts >= monthStart && s.user_id) {
        ids.add(s.user_id);
      }
    }
    return [...ids];
  });

  // Sessions that produced PRs (real outcome)
  sessionsWithPrs = computed(() =>
    this.sessions().filter(s => (s.pull_requests?.length ?? 0) > 0).length
  );

  // Total PRs from sessions list
  totalPrsFromSessions = computed(() =>
    this.sessions().reduce((acc, s) => acc + (s.pull_requests?.length ?? 0), 0)
  );

  // ACUs wasted on error sessions
  acusFromErrors = computed(() =>
    this.sessions()
      .filter(s => s.status === 'error')
      .reduce((acc, s) => acc + (s.acus_consumed ?? 0), 0)
  );

  // Outcome rate: sessions with PRs / total
  sessionSuccessRate = computed(() => {
    const total = this.totalSessions();
    return total > 0 ? (this.sessionsWithPrs() / total) * 100 : 0;
  });

  // Waste-to-Outcome: ACUs from errors / total ACUs
  wasteToOutcomeRatio = computed(() => {
    const totalAcus = this.sessions().reduce((acc, s) => acc + (s.acus_consumed ?? 0), 0);
    return totalAcus > 0 ? this.acusFromErrors() / totalAcus : 0;
  });

  // Sessions grouped by day (for Sesiones por Día chart)
  sessionsPerDay = computed(() => {
    const byDay = new Map<string, number>();
    for (const s of this.sessions()) {
      const ts = typeof s.created_at === 'number' ? s.created_at : parseInt(s.created_at as string, 10);
      if (isNaN(ts) || ts <= 0) continue;
      const d = new Date(ts * 1000);
      const key = d.toISOString().split('T')[0];
      byDay.set(key, (byDay.get(key) ?? 0) + 1);
    }
    return [...byDay.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([date, count]) => ({ date, count }));
  });

  // Legacy getters for backward compat
  finishedSessions = this.sessionsWithPrs;
  failedSessions = this.errorSessions;
  stoppedSessions = this.suspendedByUser;

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
    const apiTotal = typeof data['total'] === 'number' ? (data['total'] as number) : 0;
    this.totalSessions.set(apiTotal > 0 ? apiTotal : sessionList.length);
    this.runningSessions.set(sessionList.filter(s => s.status === 'running').length);
    this.errorSessions.set(sessionList.filter(s => s.status === 'error').length);
    this.suspendedSessions.set(sessionList.filter(s => s.status === 'suspended').length);
    this.suspendedByInactivity.set(
      sessionList.filter(s => s.status === 'suspended' && s.status_detail === 'inactivity').length
    );
    this.suspendedByUser.set(
      sessionList.filter(s => s.status === 'suspended' && s.status_detail === 'user_request').length
    );
  }
}

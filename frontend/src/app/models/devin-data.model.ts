export type SessionStatus = 'active' | 'completed' | 'failed' | 'pending';

export interface DevinSession {
  id: string;
  status: SessionStatus;
  startDate: string;
  duration: number;
}

export interface DevinData {
  sessions: DevinSession[];
  totalSessions: number;
  activeSessions: number;
  completedSessions: number;
  failedSessions: number;
  timestamp: string;
}

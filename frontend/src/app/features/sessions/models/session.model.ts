export type SessionStatus = 'running' | 'finished' | 'failed' | 'stopped' | 'suspended' | 'blocked' | 'error' | 'unknown';

export interface DevinSession {
  session_id: string;
  title?: string;
  status: SessionStatus;
  status_enum?: string;
  created_at: number | string;
  updated_at?: number | string;
  acus_consumed?: number;
  is_archived?: boolean;
  snap_count?: number;
  total_tokens?: number;
  origin?: string;
  pull_request?: {
    url?: string;
    status?: string;
  };
}

export interface SessionsResponse {
  sessions: DevinSession[];
  has_more?: boolean;
  next_cursor?: string;
  total_count?: number;
}

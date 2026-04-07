export interface Organization {
  id: string;
  name?: string;
  display_name?: string;
}

export interface User {
  id: string;
  email?: string;
  name?: string;
  role?: string;
}

export interface QueueStatus {
  status: string;
  queue_length?: number;
  estimated_wait?: string;
}

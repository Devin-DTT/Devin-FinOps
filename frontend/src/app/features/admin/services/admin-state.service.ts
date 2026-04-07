import { Injectable, signal } from '@angular/core';
import { WebSocketMessage } from '../../../models/devin-data.model';

@Injectable({ providedIn: 'root' })
export class AdminStateService {
  // Signals
  orgCount = signal(0);
  userCount = signal(0);
  hypervisorCount = signal(0);
  queueStatus = signal('unknown');
  lastUpdated = signal(0);

  handleMessage(msg: WebSocketMessage): void {
    const data = msg.data as Record<string, unknown>;
    this.lastUpdated.set(msg.timestamp);

    switch (msg.endpoint) {
      case 'list_organizations':
        this.handleOrganizations(data);
        break;
      case 'list_users':
        this.handleUsers(data);
        break;
      case 'list_hypervisors':
        this.handleHypervisors(data);
        break;
      case 'get_queue_status':
        this.queueStatus.set((data['status'] as string) ?? 'unknown');
        break;
    }
  }

  private handleOrganizations(data: Record<string, unknown>): void {
    this.orgCount.set(
      typeof data['total'] === 'number'
        ? (data['total'] as number)
        : this.extractArray(data, 'organizations').length
    );
  }

  private handleUsers(data: Record<string, unknown>): void {
    this.userCount.set(
      typeof data['total'] === 'number'
        ? (data['total'] as number)
        : this.extractArray(data, 'users').length
    );
  }

  private handleHypervisors(data: Record<string, unknown>): void {
    this.hypervisorCount.set(
      typeof data['total'] === 'number'
        ? (data['total'] as number)
        : this.extractArray(data, 'hypervisors').length
    );
  }

  private extractArray(data: Record<string, unknown>, key: string): unknown[] {
    if (Array.isArray(data)) return data;
    const value = data[key];
    if (Array.isArray(value)) return value;
    for (const k of Object.keys(data)) {
      if (Array.isArray(data[k])) return data[k] as unknown[];
    }
    return [];
  }
}

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
  memberMap = signal<Map<string, string>>(new Map());

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
        : this.extractArray(data, 'items', 'organizations').length
    );
  }

  private handleUsers(data: Record<string, unknown>): void {
    const items = this.extractArray(data, 'items', 'users') as Record<string, unknown>[];
    this.userCount.set(
      typeof data['total'] === 'number'
        ? (data['total'] as number)
        : items.length
    );
    const map = new Map<string, string>();
    for (const m of items) {
      const uid = (m['user_id'] as string) ?? (m['id'] as string) ?? '';
      const name = (m['display_name'] as string) ?? (m['username'] as string)
        ?? (m['email'] as string) ?? (m['name'] as string) ?? '';
      if (uid && name) {
        map.set(uid, name);
      }
    }
    if (map.size > 0) {
      this.memberMap.set(map);
    }
  }

  private handleHypervisors(data: Record<string, unknown>): void {
    this.hypervisorCount.set(
      typeof data['total'] === 'number'
        ? (data['total'] as number)
        : this.extractArray(data, 'items', 'hypervisors').length
    );
  }

  private extractArray(data: Record<string, unknown>, ...keys: string[]): unknown[] {
    if (Array.isArray(data)) return data;
    for (const key of keys) {
      const value = data[key];
      if (Array.isArray(value)) return value;
    }
    for (const k of Object.keys(data)) {
      if (Array.isArray(data[k])) return data[k] as unknown[];
    }
    return [];
  }
}

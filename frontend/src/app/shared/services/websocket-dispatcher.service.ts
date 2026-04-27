import { Injectable } from '@angular/core';
import { WebSocketService } from '../../core/services/websocket.service';
import { SessionsStateService } from '../../features/sessions/services/sessions-state.service';
import { BillingStateService } from '../../features/billing/services/billing-state.service';
import { MetricsStateService } from '../../features/metrics/services/metrics-state.service';
import { AdminStateService } from '../../features/admin/services/admin-state.service';
import { WebSocketMessage } from '../../models/devin-data.model';

const SESSIONS_ENDPOINTS = ['list_sessions', 'list_enterprise_sessions'];
const BILLING_ENDPOINTS = ['list_billing_cycles', 'get_daily_consumption', 'get_acu_limits'];
const METRICS_ENDPOINTS = [
  'get_dau_metrics', 'get_wau_metrics', 'get_mau_metrics',
  'get_sessions_metrics', 'get_prs_metrics', 'get_usage_metrics',
  'get_searches_metrics', 'get_active_users_metrics'
];
const ADMIN_ENDPOINTS = ['list_organizations', 'list_users', 'list_hypervisors', 'get_queue_status'];

@Injectable({ providedIn: 'root' })
export class WebSocketDispatcherService {

  constructor(
    private ws: WebSocketService,
    private sessionsState: SessionsStateService,
    private billingState: BillingStateService,
    private metricsState: MetricsStateService,
    private adminState: AdminStateService
  ) {
    this.ws.data$.subscribe(msg => this.dispatch(msg));
  }

  private dispatch(msg: WebSocketMessage): void {
    if (msg.type !== 'data' || !msg.data) return;

    if (SESSIONS_ENDPOINTS.includes(msg.endpoint)) {
      this.sessionsState.handleMessage(msg);
    } else if (BILLING_ENDPOINTS.includes(msg.endpoint)) {
      this.billingState.handleMessage(msg);
    } else if (METRICS_ENDPOINTS.includes(msg.endpoint)) {
      this.metricsState.handleMessage(msg);
    } else if (ADMIN_ENDPOINTS.includes(msg.endpoint)) {
      this.adminState.handleMessage(msg);
    } else {
      console.debug('[WS Dispatcher] Unhandled endpoint:', msg.endpoint);
    }
  }
}

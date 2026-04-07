import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { Subscription } from 'rxjs';

import { WebSocketService, ConnectionStatus } from '../../core/services/websocket.service';
import { WebSocketDispatcherService } from '../../shared/services/websocket-dispatcher.service';
import { BillingComponent } from '../billing/billing.component';
import { SessionsComponent } from '../sessions/sessions.component';
import { MetricsComponent } from '../metrics/metrics.component';
import { AdminComponent } from '../admin/admin.component';
import { BillingStateService } from '../billing/services/billing-state.service';
import { SessionsStateService } from '../sessions/services/sessions-state.service';
import { MetricsStateService } from '../metrics/services/metrics-state.service';
import { AdminStateService } from '../admin/services/admin-state.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    MatToolbarModule,
    MatIconModule,
    MatTabsModule,
    BillingComponent,
    SessionsComponent,
    MetricsComponent,
    AdminComponent
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit, OnDestroy {
  connectionStatus: ConnectionStatus = 'disconnected';
  endpointsReceived = new Set<string>();

  private wsService = inject(WebSocketService);
  private billingState = inject(BillingStateService);
  private sessionsState = inject(SessionsStateService);
  private metricsState = inject(MetricsStateService);
  private adminState = inject(AdminStateService);

  // Inject dispatcher to trigger subscription setup
  private _dispatcher = inject(WebSocketDispatcherService);

  private statusSubscription: Subscription | null = null;
  private dataSubscription: Subscription | null = null;

  get lastUpdated(): number {
    return Math.max(
      this.billingState.lastUpdated(),
      this.sessionsState.lastUpdated(),
      this.metricsState.lastUpdated(),
      this.adminState.lastUpdated()
    );
  }

  ngOnInit(): void {
    this.statusSubscription = this.wsService.connectionStatus$.subscribe((status: ConnectionStatus) => {
      this.connectionStatus = status;
    });

    this.dataSubscription = this.wsService.data$.subscribe(msg => {
      if (msg.type === 'data' && msg.endpoint) {
        this.endpointsReceived.add(msg.endpoint);
      }
    });
  }

  ngOnDestroy(): void {
    this.statusSubscription?.unsubscribe();
    this.dataSubscription?.unsubscribe();
  }
}

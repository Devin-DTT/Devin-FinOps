import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';

import { WebSocketService, ConnectionStatus } from '../../core/services/websocket.service';
import { WebSocketDispatcherService } from '../../shared/services/websocket-dispatcher.service';
import { BillingComponent } from '../billing/billing.component';
import { SessionsComponent } from '../sessions/sessions.component';
import { MetricsComponent } from '../metrics/metrics.component';
import { AdminComponent } from '../admin/admin.component';
import { BillingStateService } from '../billing/services/billing-state.service';

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
export class DashboardComponent implements OnInit {
  connectionStatus: ConnectionStatus = 'disconnected';
  endpointsReceived = new Set<string>();

  private wsService = inject(WebSocketService);
  private billingState = inject(BillingStateService);

  // Inject dispatcher to trigger subscription setup
  private _dispatcher = inject(WebSocketDispatcherService);

  get lastUpdated(): number {
    return this.billingState.lastUpdated();
  }

  ngOnInit(): void {
    this.wsService.connectionStatus$.subscribe((status: ConnectionStatus) => {
      this.connectionStatus = status;
    });

    this.wsService.data$.subscribe(msg => {
      if (msg.type === 'data' && msg.endpoint) {
        this.endpointsReceived.add(msg.endpoint);
      }
    });
  }
}

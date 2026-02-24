import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { WebsocketService, ConnectionStatus } from '../../services/websocket.service';
import { MetricsService } from '../../services/metrics.service';
import { MetricsResult } from '../../models/metrics.model';
import { MetricsDisplayComponent } from '../metrics-display/metrics-display.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, MetricsDisplayComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit, OnDestroy {
  metrics: MetricsResult | null = null;
  connectionStatus: ConnectionStatus = 'disconnected';
  errorMessage: string | null = null;
  isLoading = false;

  private subscriptions: Subscription[] = [];

  constructor(
    private wsService: WebsocketService,
    private metricsService: MetricsService
  ) {}

  ngOnInit(): void {
    this.subscriptions.push(
      this.wsService.metrics$.subscribe(metrics => {
        this.metrics = metrics;
        this.isLoading = false;
      }),
      this.wsService.status$.subscribe(status => {
        this.connectionStatus = status;
        if (status === 'error') {
          this.errorMessage = 'Connection error. Attempting to reconnect...';
        } else if (status === 'connected') {
          this.errorMessage = null;
        }
      })
    );
  }

  connect(): void {
    this.wsService.connect('ws://localhost:8080/ws/finops');
  }

  disconnect(): void {
    this.wsService.disconnect();
  }

  fetchMetrics(startDate: string, endDate: string): void {
    this.isLoading = true;
    this.wsService.fetchMetrics(startDate, endDate);
  }

  refreshMetrics(): void {
    this.isLoading = true;
    this.wsService.refreshMetrics();
  }

  get totalCost(): number {
    return this.metricsService.getTotalCost(this.metrics);
  }

  get totalAcus(): number {
    return this.metricsService.getTotalAcus(this.metrics);
  }

  get totalSessions(): number {
    return this.metricsService.getTotalSessions(this.metrics);
  }

  get uniqueUsers(): number {
    return this.metricsService.getUniqueUsers(this.metrics);
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(sub => sub.unsubscribe());
  }
}

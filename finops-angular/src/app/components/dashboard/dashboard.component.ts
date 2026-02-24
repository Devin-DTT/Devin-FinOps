import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { WebsocketService } from '../../services/websocket.service';
import { FinopsService } from '../../services/finops.service';
import { MetricsCardsComponent } from '../metrics-cards/metrics-cards.component';
import { FiltersComponent } from '../filters/filters.component';
import { ChartsComponent } from '../charts/charts.component';

/** Default WebSocket URL for the Java backend */
const WS_URL = 'ws://localhost:8080/ws/finops';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, MetricsCardsComponent, FiltersComponent, ChartsComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit, OnDestroy {

  wsConnected = false;
  private subscription = new Subscription();

  constructor(
    private websocketService: WebsocketService,
    private finopsService: FinopsService
  ) {}

  ngOnInit(): void {
    // Subscribe to WebSocket connection status
    this.subscription.add(
      this.websocketService.connectionStatus$.subscribe(connected => {
        this.wsConnected = connected;
        if (connected) {
          // Request metrics from backend on connect
          this.websocketService.fetchMetrics(null, null);
        }
      })
    );

    // Subscribe to WebSocket messages
    this.subscription.add(
      this.websocketService.messages$.subscribe(msg => {
        if (msg.type === 'metrics' && msg.data) {
          // TODO: Transform backend MetricsResult into DailyConsumption[]
          // For now, real-time data will be handled when backend is running
          console.log('Received metrics from WebSocket:', msg.data);
        }
        if (msg.type === 'error') {
          console.error('WebSocket error:', msg.message);
        }
      })
    );

    // Try to connect to WebSocket backend
    this.websocketService.connect(WS_URL);

    // Load mock data as fallback (will be replaced when WebSocket sends real data)
    setTimeout(() => {
      if (!this.wsConnected) {
        const mockData = this.finopsService.generateMockData();
        this.finopsService.loadData(mockData);
      }
    }, 2000);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    this.websocketService.disconnect();
  }
}

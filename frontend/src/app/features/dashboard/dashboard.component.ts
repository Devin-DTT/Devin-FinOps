import { Component, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';

import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSort, MatSortModule } from '@angular/material/sort';

import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartData } from 'chart.js';

import { WebSocketService, ConnectionStatus } from '../../core/services/websocket.service';
import { DevinData, DevinSession, SessionStatus } from '../../models/devin-data.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatCardModule,
    MatChipsModule,
    MatFormFieldModule,
    MatSelectModule,
    MatIconModule,
    MatToolbarModule,
    MatSortModule,
    BaseChartDirective
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit, OnDestroy {
  @ViewChild(MatSort) sort!: MatSort;

  displayedColumns: string[] = ['id', 'status', 'startDate', 'duration'];
  dataSource = new MatTableDataSource<DevinSession>([]);
  connectionStatus: ConnectionStatus = 'disconnected';

  totalSessions = 0;
  activeSessions = 0;
  completedSessions = 0;
  failedSessions = 0;

  selectedStatusFilter: SessionStatus | 'all' = 'all';
  statusOptions: Array<SessionStatus | 'all'> = ['all', 'active', 'completed', 'failed', 'pending'];

  // Chart configuration
  lineChartData: ChartData<'line'> = {
    labels: [],
    datasets: [
      {
        data: [],
        label: 'Active Sessions',
        fill: true,
        tension: 0.4,
        borderColor: '#3f51b5',
        backgroundColor: 'rgba(63, 81, 181, 0.1)'
      }
    ]
  };

  lineChartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      x: {
        title: { display: true, text: 'Time' }
      },
      y: {
        title: { display: true, text: 'Active Sessions' },
        beginAtZero: true
      }
    },
    plugins: {
      legend: {
        display: true,
        position: 'top'
      }
    }
  };

  private dataSubscription: Subscription | null = null;
  private statusSubscription: Subscription | null = null;
  private chartHistory: { time: string; count: number }[] = [];
  private readonly maxChartPoints = 20;

  constructor(private wsService: WebSocketService) {}

  ngOnInit(): void {
    this.dataSubscription = this.wsService.data$.subscribe((data: DevinData) => {
      this.updateDashboard(data);
    });

    this.statusSubscription = this.wsService.connectionStatus$.subscribe((status: ConnectionStatus) => {
      this.connectionStatus = status;
    });
  }

  ngOnDestroy(): void {
    this.dataSubscription?.unsubscribe();
    this.statusSubscription?.unsubscribe();
  }

  onStatusFilterChange(): void {
    this.applyFilter();
  }

  getStatusColor(status: SessionStatus): string {
    const colorMap: Record<SessionStatus, string> = {
      active: 'primary',
      completed: 'accent',
      failed: 'warn',
      pending: ''
    };
    return colorMap[status] || '';
  }

  formatDuration(seconds: number): string {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;
    return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  }

  private updateDashboard(data: DevinData): void {
    this.totalSessions = data.totalSessions;
    this.activeSessions = data.activeSessions;
    this.completedSessions = data.completedSessions;
    this.failedSessions = data.failedSessions;

    this.dataSource.data = data.sessions;
    if (this.sort) {
      this.dataSource.sort = this.sort;
    }
    this.applyFilter();

    this.updateChart(data);
  }

  private applyFilter(): void {
    if (this.selectedStatusFilter === 'all') {
      this.dataSource.filter = '';
    } else {
      this.dataSource.filterPredicate = (session: DevinSession, filter: string) => {
        return session.status === filter;
      };
      this.dataSource.filter = this.selectedStatusFilter;
    }
  }

  private updateChart(data: DevinData): void {
    const now = new Date(data.timestamp);
    const timeLabel = now.toLocaleTimeString();

    this.chartHistory.push({ time: timeLabel, count: data.activeSessions });

    if (this.chartHistory.length > this.maxChartPoints) {
      this.chartHistory.shift();
    }

    this.lineChartData = {
      ...this.lineChartData,
      labels: this.chartHistory.map(h => h.time),
      datasets: [
        {
          ...this.lineChartData.datasets[0],
          data: this.chartHistory.map(h => h.count)
        }
      ]
    };
  }
}

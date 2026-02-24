import { Component, OnInit, OnDestroy, ViewChild, ElementRef, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { Chart, registerables } from 'chart.js';
import { FinopsService } from '../../services/finops.service';
import { DailyConsumption } from '../../models/consumption-data.model';

// Register all Chart.js components
Chart.register(...registerables);

/** Color palette for the doughnut chart */
const DOUGHNUT_COLORS = [
  'rgba(59, 130, 246, 0.8)',   // Blue
  'rgba(16, 185, 129, 0.8)',   // Green
  'rgba(251, 146, 60, 0.8)',   // Orange
  'rgba(139, 92, 246, 0.8)',   // Purple
  'rgba(236, 72, 153, 0.8)',   // Pink
  'rgba(34, 197, 94, 0.8)',    // Lime
  'rgba(239, 68, 68, 0.8)',    // Red
  'rgba(14, 165, 233, 0.8)',   // Sky
  'rgba(168, 85, 247, 0.8)',   // Violet
  'rgba(245, 158, 11, 0.8)'    // Amber
];

@Component({
  selector: 'app-charts',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './charts.component.html',
  styleUrl: './charts.component.css'
})
export class ChartsComponent implements OnInit, AfterViewInit, OnDestroy {

  @ViewChild('consumptionChart') consumptionChartRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('distributionChart') distributionChartRef!: ElementRef<HTMLCanvasElement>;

  viewType: 'daily' | 'weekly' = 'daily';
  distributionType: 'user' | 'organization' = 'user';

  private consumptionChart: Chart | null = null;
  private distributionChart: Chart | null = null;
  private subscription = new Subscription();
  private filteredData: DailyConsumption[] = [];
  private viewReady = false;

  constructor(private finopsService: FinopsService) {}

  ngOnInit(): void {
    this.subscription.add(
      this.finopsService.filteredData$.subscribe(data => {
        this.filteredData = data;
        if (this.viewReady) {
          this.updateConsumptionChart();
          this.updateDistributionChart();
        }
      })
    );
  }

  ngAfterViewInit(): void {
    this.viewReady = true;
    // Initial render once view is ready and data is available
    if (this.filteredData.length > 0) {
      this.updateConsumptionChart();
      this.updateDistributionChart();
    }
  }

  setViewType(type: 'daily' | 'weekly'): void {
    this.viewType = type;
    this.finopsService.updateFilters({ viewType: type });
    this.updateConsumptionChart();
  }

  setDistributionType(type: 'user' | 'organization'): void {
    this.distributionType = type;
    this.finopsService.updateFilters({ distributionType: type });
    this.updateDistributionChart();
  }

  private updateConsumptionChart(): void {
    if (!this.consumptionChartRef) return;

    let chartData: { date: string; acus: number; cost: number }[];
    if (this.viewType === 'weekly') {
      chartData = this.finopsService.aggregateByWeek(this.filteredData);
    } else {
      // Aggregate by date for daily view
      const dailyMap: Record<string, { acus: number; cost: number }> = {};
      this.filteredData.forEach(item => {
        if (!dailyMap[item.date]) {
          dailyMap[item.date] = { acus: 0, cost: 0 };
        }
        dailyMap[item.date].acus += item.acus;
        dailyMap[item.date].cost += item.cost;
      });
      chartData = Object.keys(dailyMap).sort().map(date => ({
        date,
        acus: parseFloat(dailyMap[date].acus.toFixed(2)),
        cost: parseFloat(dailyMap[date].cost.toFixed(2))
      }));
    }

    const labels = chartData.map(item => item.date);
    const acusData = chartData.map(item => item.acus);
    const costData = chartData.map(item => item.cost);

    if (this.consumptionChart) {
      this.consumptionChart.destroy();
    }

    const ctx = this.consumptionChartRef.nativeElement.getContext('2d');
    if (!ctx) return;

    this.consumptionChart = new Chart(ctx, {
      type: 'line',
      data: {
        labels,
        datasets: [
          {
            label: 'ACUs Consumidos',
            data: acusData,
            borderColor: 'rgb(59, 130, 246)',
            backgroundColor: 'rgba(59, 130, 246, 0.1)',
            borderWidth: 2,
            fill: true,
            tension: 0.4,
            pointRadius: 3,
            pointHoverRadius: 5,
            yAxisID: 'y'
          },
          {
            label: 'Coste Diario ($)',
            data: costData,
            borderColor: 'rgb(16, 185, 129)',
            backgroundColor: 'rgba(16, 185, 129, 0.1)',
            borderWidth: 2,
            fill: true,
            tension: 0.4,
            pointRadius: 3,
            pointHoverRadius: 5,
            yAxisID: 'yCost'
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: { display: true, position: 'top' },
          tooltip: {
            mode: 'index',
            intersect: false,
            callbacks: {
              label: (context) => {
                let label = context.dataset.label || '';
                if (label) label += ': ';
                if (context.parsed.y !== null) {
                  label += context.datasetIndex === 0
                    ? context.parsed.y.toFixed(2) + ' ACUs'
                    : '$' + context.parsed.y.toFixed(2);
                }
                return label;
              }
            }
          }
        },
        scales: {
          x: {
            display: true,
            title: { display: true, text: 'Fecha' },
            ticks: { maxRotation: 45, minRotation: 45, autoSkip: true, maxTicksLimit: 20 }
          },
          y: {
            type: 'linear',
            display: true,
            position: 'left',
            title: { display: true, text: 'ACUs' },
            beginAtZero: true
          },
          yCost: {
            type: 'linear',
            display: true,
            position: 'right',
            title: { display: true, text: 'Coste ($)' },
            beginAtZero: true,
            grid: { drawOnChartArea: false }
          }
        }
      }
    });
  }

  private updateDistributionChart(): void {
    if (!this.distributionChartRef) return;

    const aggregated = this.finopsService.aggregateByGroup(this.filteredData, this.distributionType);
    const labels = Object.keys(aggregated).sort();
    const values = labels.map(l => parseFloat(aggregated[l].toFixed(2)));

    if (this.distributionChart) {
      this.distributionChart.destroy();
    }

    const ctx = this.distributionChartRef.nativeElement.getContext('2d');
    if (!ctx) return;

    this.distributionChart = new Chart(ctx, {
      type: 'doughnut',
      data: {
        labels,
        datasets: [{
          label: 'ACUs Consumidos',
          data: values,
          backgroundColor: DOUGHNUT_COLORS.slice(0, labels.length),
          borderWidth: 2,
          borderColor: '#ffffff'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            display: true,
            position: 'bottom',
            labels: { boxWidth: 12, padding: 8, font: { size: 11 } }
          },
          tooltip: {
            callbacks: {
              label: (context) => {
                const label = context.label || '';
                const value = context.parsed;
                const total = (context.dataset.data as number[]).reduce((a, b) => a + b, 0);
                const percentage = ((value / total) * 100).toFixed(1);
                return `${label}: ${value} ACUs (${percentage}%)`;
              }
            }
          }
        }
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    if (this.consumptionChart) this.consumptionChart.destroy();
    if (this.distributionChart) this.distributionChart.destroy();
  }
}

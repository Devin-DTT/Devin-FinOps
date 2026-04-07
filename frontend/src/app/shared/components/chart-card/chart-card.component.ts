import { Component, Input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartData, ChartType } from 'chart.js';

@Component({
  selector: 'app-chart-card',
  standalone: true,
  imports: [MatCardModule, BaseChartDirective],
  template: `
    <mat-card class="chart-card">
      <mat-card-header>
        <mat-card-title>{{ title }}</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <div class="chart-container">
          <canvas baseChart
                  [data]="chartData"
                  [options]="chartOptions"
                  [type]="chartType">
          </canvas>
        </div>
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .chart-card .chart-container {
      height: 300px;
      position: relative;
    }
  `]
})
export class ChartCardComponent {
  @Input() title = '';
  @Input() chartData!: ChartData;
  @Input() chartOptions: ChartConfiguration['options'] = {};
  @Input() chartType: ChartType = 'bar';
}

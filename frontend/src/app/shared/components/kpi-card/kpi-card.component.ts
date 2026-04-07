import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';

@Component({
  selector: 'app-kpi-card',
  standalone: true,
  imports: [CommonModule, MatCardModule],
  template: `
    <mat-card class="kpi-card" [ngClass]="cssClass">
      <mat-card-header>
        <mat-card-title>{{ title }}</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <span class="kpi-value">{{ formattedValue }}</span>
        <span class="kpi-sub" *ngIf="subtitle">{{ subtitle }}</span>
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .kpi-card {
      text-align: center;
    }
    .kpi-value {
      font-size: 42px;
      font-weight: 700;
      display: block;
      margin-top: 8px;
    }
    .kpi-sub {
      font-size: 13px;
      color: rgba(0, 0, 0, 0.54);
      display: block;
      margin-top: 4px;
    }
    .kpi-acu .kpi-value { color: #ff9800; }
  `]
})
export class KpiCardComponent {
  @Input() title = '';
  @Input() value: number | string = 0;
  @Input() format: string | null = null;
  @Input() subtitle = '';
  @Input() cssClass = '';

  get formattedValue(): string {
    if (typeof this.value === 'string') return this.value;
    if (this.format === 'decimal') return this.value.toFixed(1);
    if (this.format === 'decimal2') return this.value.toFixed(2);
    if (this.format === 'decimal3') return this.value.toFixed(3);
    if (this.format === 'integer') return Math.round(this.value).toString();
    if (this.format === 'percent') return this.value.toFixed(1) + ' %';
    return this.value.toString();
  }
}

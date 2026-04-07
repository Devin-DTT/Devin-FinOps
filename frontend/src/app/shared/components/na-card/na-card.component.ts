import { Component, Input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-na-card',
  standalone: true,
  imports: [MatCardModule, MatIconModule],
  template: `
    <mat-card class="kpi-card kpi-card-na">
      <mat-card-header>
        <mat-card-title><mat-icon>info</mat-icon> {{ title }}</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <span class="kpi-value-na">N/A</span>
        <span class="kpi-sub-na">{{ reason }}</span>
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .kpi-card-na {
      text-align: center;
      background: rgba(0,0,0,0.03);
      border: 1px dashed rgba(0,0,0,0.15);
    }
    .kpi-value-na {
      font-size: 28px;
      font-weight: 700;
      color: rgba(0,0,0,0.25);
      display: block;
      margin-top: 8px;
    }
    .kpi-sub-na {
      font-size: 11px;
      color: rgba(0,0,0,0.35);
      display: block;
      margin-top: 4px;
      font-style: italic;
    }
    mat-icon {
      color: rgba(0,0,0,0.2);
      font-size: 18px;
      vertical-align: middle;
    }
  `]
})
export class NaCardComponent {
  @Input() title = '';
  @Input() reason = 'No disponible en API';
}

import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { FinopsService } from '../../services/finops.service';
import { ExecutiveMetrics } from '../../models/consumption-data.model';

@Component({
  selector: 'app-metrics-cards',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './metrics-cards.component.html',
  styleUrl: './metrics-cards.component.css'
})
export class MetricsCardsComponent implements OnInit, OnDestroy {

  metrics: ExecutiveMetrics = {
    acusMes: 0, acusMesAnterior: 0,
    costeMes: 0, costeMesAnterior: 0,
    diferenciaACUs: 0, diferenciaCost: 0,
    porcentajeACUs: 0, porcentajeCost: 0
  };

  currentMonthName = '';
  currentYear = 0;
  prevMonthName = '';
  prevYear = 0;

  private subscription = new Subscription();

  constructor(public finopsService: FinopsService) {}

  ngOnInit(): void {
    const today = new Date();
    const currentMonth = today.getMonth();
    this.currentYear = today.getFullYear();
    const prevMonth = currentMonth === 0 ? 11 : currentMonth - 1;
    this.prevYear = currentMonth === 0 ? this.currentYear - 1 : this.currentYear;

    this.currentMonthName = this.finopsService.getSpanishMonthName(currentMonth);
    this.prevMonthName = this.finopsService.getSpanishMonthName(prevMonth);

    this.subscription.add(
      this.finopsService.metrics$.subscribe(m => this.metrics = m)
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}

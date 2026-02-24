import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { FinopsService } from '../../services/finops.service';

@Component({
  selector: 'app-filters',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './filters.component.html',
  styleUrl: './filters.component.css'
})
export class FiltersComponent implements OnInit, OnDestroy {

  availableMonths: string[] = [];
  availableUsers: string[] = [];
  availableOrgs: string[] = [];
  currentDays: number | null = 90;

  private subscription = new Subscription();

  constructor(public finopsService: FinopsService) {}

  ngOnInit(): void {
    this.subscription.add(
      this.finopsService.allData$.subscribe(() => {
        this.availableMonths = this.finopsService.getAvailableMonths();
        this.availableUsers = this.finopsService.getAvailableUsers();
        this.availableOrgs = this.finopsService.getAvailableOrganizations();
      })
    );
  }

  onMonthChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value || null;
    this.currentDays = null;
    this.finopsService.updateFilters({ month: value, days: null });
  }

  onDaysFilter(days: number): void {
    this.currentDays = days;
    this.finopsService.updateFilters({ days, month: null });
  }

  onUserChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value || null;
    this.finopsService.updateFilters({ user: value });
  }

  onOrgChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value || null;
    this.finopsService.updateFilters({ organization: value });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}

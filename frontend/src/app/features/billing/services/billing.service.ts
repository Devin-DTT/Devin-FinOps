import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { FinOpsKpis } from '../models/billing.model';
import { environment } from '../../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class BillingService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiUrl + '/billing';

  getBillingCycles(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/cycles`);
  }

  getDailyConsumption(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/consumption/daily`);
  }

  getAcuLimits(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/acu-limits`);
  }

  setOrgAcuLimit(orgId: string, limit: number): Observable<unknown> {
    return this.http.put(`${this.baseUrl}/acu-limits/orgs/${orgId}`, { limit });
  }

  deleteOrgAcuLimit(orgId: string): Observable<unknown> {
    return this.http.delete(`${this.baseUrl}/acu-limits/orgs/${orgId}`);
  }

  getOrgGroupLimits(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/org-group-limits`);
  }

  getFinOpsKpis(): Observable<FinOpsKpis> {
    return this.http.get<FinOpsKpis>(`${this.baseUrl}/finops-kpis`);
  }
}

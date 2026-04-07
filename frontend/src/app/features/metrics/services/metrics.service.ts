import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class MetricsService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiUrl + '/metrics';

  getDauMetrics(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/dau`);
  }

  getWauMetrics(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/wau`);
  }

  getMauMetrics(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/mau`);
  }

  getActiveUsersMetrics(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/active-users`);
  }

  getSessionsMetrics(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/sessions`);
  }

  getSearchesMetrics(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/searches`);
  }

  getPrsMetrics(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/prs`);
  }

  getUsageMetrics(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/usage`);
  }
}

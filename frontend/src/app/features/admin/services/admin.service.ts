import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiUrl + '/admin';

  listOrganizations(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/organizations`);
  }

  listUsers(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/users`);
  }

  listRoles(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/roles`);
  }

  listIdpGroups(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/idp-groups`);
  }

  createIdpGroups(body: Record<string, unknown>): Observable<unknown> {
    return this.http.post(`${this.baseUrl}/idp-groups`, body);
  }

  deleteIdpGroup(name: string): Observable<unknown> {
    return this.http.delete(`${this.baseUrl}/idp-groups/${name}`);
  }

  listKnowledge(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/knowledge`);
  }

  listPlaybooks(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/playbooks`);
  }

  listSecrets(orgId?: string): Observable<unknown> {
    const params = orgId ? { orgId } : {};
    return this.http.get(`${this.baseUrl}/secrets`, { params });
  }

  listGitConnections(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/git/connections`);
  }

  listHypervisors(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/infrastructure/hypervisors`);
  }

  getQueueStatus(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/infrastructure/queue`);
  }

  getIpAccessList(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/security/ip-access-list`);
  }

  listAuditLogs(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/audit/logs`);
  }
}

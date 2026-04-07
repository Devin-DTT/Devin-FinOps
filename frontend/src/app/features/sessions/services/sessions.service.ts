import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class SessionsService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiUrl + '/sessions';

  listSessions(): Observable<unknown> {
    return this.http.get(this.baseUrl);
  }

  getSession(sessionId: string): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/${sessionId}`);
  }

  getSessionMessages(sessionId: string): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/${sessionId}/messages`);
  }

  createSession(prompt: string): Observable<unknown> {
    return this.http.post(this.baseUrl, { prompt });
  }

  sendMessage(sessionId: string, message: string): Observable<unknown> {
    return this.http.post(`${this.baseUrl}/${sessionId}/messages`, { message });
  }

  archiveSession(sessionId: string): Observable<unknown> {
    return this.http.post(`${this.baseUrl}/${sessionId}/archive`, {});
  }

  terminateSession(sessionId: string): Observable<unknown> {
    return this.http.post(`${this.baseUrl}/${sessionId}/terminate`, {});
  }

  deleteSession(sessionId: string): Observable<unknown> {
    return this.http.delete(`${this.baseUrl}/${sessionId}`);
  }

  getSessionTags(sessionId: string): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/${sessionId}/tags`);
  }

  updateSessionTags(sessionId: string, tags: string[]): Observable<unknown> {
    return this.http.put(`${this.baseUrl}/${sessionId}/tags`, { tags });
  }

  getSessionInsights(sessionId: string): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/${sessionId}/insights`);
  }

  listSchedules(): Observable<unknown> {
    return this.http.get(`${this.baseUrl}/schedules`);
  }

  createSchedule(body: Record<string, unknown>): Observable<unknown> {
    return this.http.post(`${this.baseUrl}/schedules`, body);
  }
}

import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DashboardStatsDTO } from '../models/interfaces';

@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private apiUrl = 'http://localhost:8087/api/admin/dashboard';
  private iaApiUrl = 'http://localhost:8087/api/admin/ia';

  constructor(private http: HttpClient) {}

  getDashboardStats(months: number = 6, department?: string): Observable<DashboardStatsDTO> {
    let params = new HttpParams().set('months', months.toString());
    if (department && department.trim() !== '') {
      params = params.set('department', department.trim());
    }
    return this.http.get<DashboardStatsDTO>(`${this.apiUrl}/stats`, { params });
  }

  relaunchAiAnalysis(candidateId: number): Observable<any> {
    return this.http.post<any>(`${this.iaApiUrl}/candidates/${candidateId}/analyze`, {});
  }
}

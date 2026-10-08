import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JobOffer } from '../models/interfaces';
import { environment } from '../../../environments/environment.prod';

@Injectable({
  providedIn: 'root'
})
export class CandidateService {
  private publicApiUrl = `${environment.apiUrl}/public`;
  private candidateApiUrl = `${environment.apiUrl}/recruitment/candidates`;

  constructor(private http: HttpClient) {}

  getPublicOffer(id: number): Observable<JobOffer> {
    return this.http.get<JobOffer>(`${this.publicApiUrl}/offers/${id}`);
  }

  getAllPublicOffers(): Observable<JobOffer[]> {
    return this.http.get<JobOffer[]>(`${this.publicApiUrl}/offers`);
  }

  apply(candidateData: string, cv: File, motivationLetter: File): Observable<any> {
    const formData = new FormData();
    formData.append('candidate', candidateData);
    formData.append('cv', cv);
    formData.append('motivationLetter', motivationLetter);

    return this.http.post(this.candidateApiUrl, formData);
  }

  // ==========================================
  // SECURED ENDPOINTS (Admin/RH/Manager)
  // ==========================================

  getAllCandidates(): Observable<any[]> {
    return this.http.get<any[]>(this.candidateApiUrl);
  }

  getAssignedCandidates(): Observable<any[]> {
    return this.http.get<any[]>(`${this.candidateApiUrl}/assigned-to-me`);
  }

  updateStatus(id: number, status: string): Observable<any> {
    return this.http.put<any>(`${this.candidateApiUrl}/${id}/status?status=${status}`, {});
  }

  transmitToManager(id: number, managerId: number): Observable<any> {
    return this.http.put<any>(`${this.candidateApiUrl}/${id}/transmit?managerId=${managerId}`, {});
  }

  getCandidateById(id: number): Observable<any> {
    return this.http.get<any>(`${this.candidateApiUrl}/${id}`);
  }

  updateCandidate(id: number, candidateData: any): Observable<any> {
    return this.http.put<any>(`${this.candidateApiUrl}/${id}`, candidateData);
  }

  downloadCv(id: number): Observable<Blob> {
    return this.http.get(`${this.candidateApiUrl}/${id}/cv`, { responseType: 'blob' });
  }

  downloadMotivationLetter(id: number): Observable<Blob> {
    return this.http.get(`${this.candidateApiUrl}/${id}/motivation-letter`, { responseType: 'blob' });
  }

  relancerAnalyse(id: number): Observable<any> {
    return this.http.post<any>(`${environment.apiUrl}/admin/ia/candidates/${id}/analyze`, {});
  }
}

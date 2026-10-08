import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Interview, InterviewNotes, InterviewRequest, InterviewStatus } from '../models/interfaces';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class InterviewService {
  private apiUrl = `${environment.apiUrl}/recruitment/interviews`;

  constructor(private http: HttpClient) {}

  scheduleInterview(request: InterviewRequest): Observable<Interview> {
    return this.http.post<Interview>(this.apiUrl, request);
  }

  updateInterview(id: number, request: InterviewRequest): Observable<Interview> {
    return this.http.put<Interview>(`${this.apiUrl}/${id}`, request);
  }

  getInterviewsByCandidate(candidateId: number): Observable<Interview[]> {
    return this.http.get<Interview[]>(`${this.apiUrl}/candidate/${candidateId}`);
  }

  getMyInterviews(): Observable<Interview[]> {
    return this.http.get<Interview[]>(`${this.apiUrl}/my-interviews`);
  }

  getInterviewById(id: number): Observable<Interview> {
    return this.http.get<Interview>(`${this.apiUrl}/${id}`);
  }

  updateInterviewStatus(id: number, status: InterviewStatus): Observable<Interview> {
    return this.http.put<Interview>(`${this.apiUrl}/${id}/status?status=${status}`, {});
  }

  resendInterviewEmail(id: number): Observable<Interview> {
    return this.http.post<Interview>(`${this.apiUrl}/${id}/resend-email`, {});
  }

  cancelInterview(id: number, reason?: string): Observable<Interview> {
    const params = reason ? `?reason=${encodeURIComponent(reason)}` : '';
    return this.http.post<Interview>(`${this.apiUrl}/${id}/cancel${params}`, {});
  }

  regenerateQuestions(id: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/questions/regenerate`, {});
  }

  saveNotes(id: number, notes: InterviewNotes): Observable<Interview> {
    return this.http.put<Interview>(`${this.apiUrl}/${id}/notes`, notes);
  }

  submitFeedback(id: number, feedback: { recommendation: string; rating: number; comment?: string }): Observable<Interview> {
    return this.http.post<Interview>(`${this.apiUrl}/${id}/feedback`, feedback);
  }
}

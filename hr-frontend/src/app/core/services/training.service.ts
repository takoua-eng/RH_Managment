import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Training, TrainingEnrollment } from '../models/interfaces';

@Injectable({
  providedIn: 'root'
})
export class TrainingService {
  private apiUrl = 'http://localhost:8087/api/trainings';

  constructor(private http: HttpClient) {}

  public getTrainingCatalog(): Observable<Training[]> {
    return this.http.get<Training[]>(this.apiUrl);
  }

  public getMyEnrollments(): Observable<TrainingEnrollment[]> {
    return this.http.get<TrainingEnrollment[]>(`${this.apiUrl}/my-enrollments`);
  }

  public getEmployeeEnrollments(employeeId: number): Observable<TrainingEnrollment[]> {
    return this.http.get<TrainingEnrollment[]>(`${this.apiUrl}/employee/${employeeId}`);
  }

  public enrollInTraining(trainingId: number): Observable<TrainingEnrollment> {
    return this.http.post<TrainingEnrollment>(`${this.apiUrl}/enroll/${trainingId}`, {});
  }

  public updateProgress(enrollmentId: number): Observable<TrainingEnrollment> {
    return this.http.post<TrainingEnrollment>(`${this.apiUrl}/progress/${enrollmentId}`, {});
  }

  public downloadCertificate(enrollmentId: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/certificate/${enrollmentId}`, { responseType: 'blob' });
  }

  public getTeamTrainings(managerId: number): Observable<TrainingEnrollment[]> {
    return this.http.get<TrainingEnrollment[]>(`http://localhost:8087/api/manager/${managerId}/team-trainings`);
  }

  // Admin/RH methods
  public createTraining(training: Partial<Training>): Observable<Training> {
    return this.http.post<Training>(this.apiUrl, training);
  }

  public updateTraining(id: number, training: Partial<Training>): Observable<Training> {
    return this.http.put<Training>(`${this.apiUrl}/${id}`, training);
  }

  public deleteTraining(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}

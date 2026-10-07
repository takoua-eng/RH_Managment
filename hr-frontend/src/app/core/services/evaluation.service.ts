import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Evaluation } from '../models/interfaces';

@Injectable({
  providedIn: 'root'
})
export class EvaluationService {
  private apiUrl = `http://localhost:8087/api/evaluations`;

  constructor(private http: HttpClient) {}

  // Manager endpoints
  getManagerEvaluations(managerId: number): Observable<Evaluation[]> {
    return this.http.get<Evaluation[]>(`${this.apiUrl}/manager/${managerId}`);
  }

  createEvaluation(evaluation: Partial<Evaluation>): Observable<Evaluation> {
    return this.http.post<Evaluation>(`${this.apiUrl}/manager`, evaluation);
  }

  updateEvaluation(id: number, evaluation: Partial<Evaluation>): Observable<Evaluation> {
    return this.http.put<Evaluation>(`${this.apiUrl}/manager/${id}`, evaluation);
  }

  deleteEvaluation(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/manager/${id}`);
  }

  // Employee endpoints
  getEmployeeEvaluations(employeeId: number): Observable<Evaluation[]> {
    return this.http.get<Evaluation[]>(`${this.apiUrl}/employee/${employeeId}`);
  }
}

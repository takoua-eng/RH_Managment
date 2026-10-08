import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import { LeaveRequest, LeaveType } from '../models/interfaces';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class LeaveService {
  private apiUrl = `${environment.apiUrl}/leaves`;
  private decisionsUrl = `${environment.apiUrl}/leave-decisions`;

  /**
   * Dernière liste de congés reçue du backend (aucune donnée fictive).
   * Vide tant que le backend n'a pas répondu.
   */
  private leavesSubject = new BehaviorSubject<LeaveRequest[]>([]);
  public leaves$ = this.leavesSubject.asObservable();

  constructor(private http: HttpClient) {
    // Nettoie les anciennes données fictives laissées dans le navigateur
    localStorage.removeItem('hr_leaves');
  }

  /** Dernière liste reçue du backend (vide si elle n'a pas encore été chargée). */
  public getLeaves(): LeaveRequest[] {
    return this.leavesSubject.value;
  }

  /** Nombre de demandes en attente dans la dernière liste reçue du backend. */
  public getPendingCount(): number {
    return this.leavesSubject.value.filter(l => l.status === 'PENDING').length;
  }

  // =====================================================================
  // Consultation
  // =====================================================================

  /** Toutes les demandes de congé (admin / RH). */
  public getAllLeaves(): Observable<LeaveRequest[]> {
    return this.http.get<LeaveRequest[]>(this.apiUrl).pipe(
      tap(leaves => this.leavesSubject.next(leaves || []))
    );
  }

  /**
   * Statistiques calculées par le backend.
   * En cas d'erreur, le composant calcule lui-même les indicateurs à partir des vrais congés.
   */
  public getStats(): Observable<{ pendingCount: number, absentToday: number, approvedThisMonth: number, presenceRate: number }> {
    return this.http.get<{ pendingCount: number, absentToday: number, approvedThisMonth: number, presenceRate: number }>(
      `${this.apiUrl}/stats`
    );
  }

  public getCalendarAbsences(year: number, month: number): Observable<{
    id?: number;
    employeeId?: number;
    employeeName: string;
    dates: string[];
    type: string;
    startDate?: string;
    endDate?: string;
    daysCount?: number;
    status?: string;
    reason?: string;
  }[]> {
    const params = new HttpParams().set('year', year.toString()).set('month', month.toString());
    return this.http.get<any[]>(`${this.apiUrl}/calendar`, { params }).pipe(
      catchError(err => {
        console.warn('Backend calendar absences endpoint failed:', err);
        return of([]);
      })
    );
  }

  public getPendingLeaves(): Observable<LeaveRequest[]> {
    return this.http.get<LeaveRequest[]>(`${this.apiUrl}/pending`);
  }

  public getLeaveHistory(employeeId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/employee/${employeeId}/history`);
  }

  // =====================================================================
  // Demande et annulation
  // =====================================================================

  /** Nouvelle demande : créée au statut PENDING, en attente de la décision du manager. */
  public requestLeave(leave: { employeeId: number, startDate: string, endDate: string, type: LeaveType, reason?: string }): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/request`, leave);
  }

  public cancelLeave(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  // =====================================================================
  // Décision (manager de l'employé, ou admin si l'employé n'a pas de manager)
  // =====================================================================

  public approveLeaveDecision(id: number, comment?: string): Observable<LeaveRequest> {
    return this.http.put<LeaveRequest>(`${this.decisionsUrl}/${id}/approve`, { comment: comment ?? null });
  }

  /** Le motif est obligatoire : le backend refuse un refus sans motif. */
  public rejectLeaveDecision(id: number, comment: string): Observable<LeaveRequest> {
    return this.http.put<LeaveRequest>(`${this.decisionsUrl}/${id}/reject`, { comment });
  }

  /**
   * Anciennes méthodes conservées pour ne pas casser les autres pages :
   * elles utilisent maintenant le nouveau circuit de décision.
   */
  public approveLeave(id: number): Observable<LeaveRequest> {
    return this.approveLeaveDecision(id);
  }

  public rejectLeave(id: number, comment?: string): Observable<LeaveRequest> {
    return this.rejectLeaveDecision(id, comment ?? '');
  }
}
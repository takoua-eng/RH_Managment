import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type LeaveStatus =
  | 'PENDING'
  | 'APPROVED_RH'
  | 'APPROVED_MANAGER'
  | 'REJECTED';

export type LeaveType = 'PAID' | 'SICK' | 'RTT' | 'OTHER';

export interface ManagerLeave {
  id: number;
  employeeId: number;
  employeeName: string;
  startDate: string;
  endDate: string;
  type: LeaveType;
  status: LeaveStatus;
  reason?: string;
  createdAt?: string;
}

const API_BASE = `${environment.backendUrl}`;

@Injectable({ providedIn: 'root' })
export class ManagerLeaveService {

  constructor(private http: HttpClient) {}

  /**
   * GET /api/manager/{managerId}/leaves?status=...
   */
  getTeamLeaves(managerId: number, status?: LeaveStatus): Observable<ManagerLeave[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    return this.http.get<ManagerLeave[]>(
      `${API_BASE}/api/manager/${managerId}/leaves`,
      { params }
    );
  }

  /**
   * PUT /api/manager/{managerId}/leaves/{leaveId}/approve
   */
  approve(managerId: number, leaveId: number): Observable<ManagerLeave> {
    return this.http.put<ManagerLeave>(
      `${API_BASE}/api/manager/${managerId}/leaves/${leaveId}/approve`,
      {}
    );
  }

  /**
   * PUT /api/manager/{managerId}/leaves/{leaveId}/reject
   */
  reject(managerId: number, leaveId: number, reason?: string): Observable<ManagerLeave> {
    return this.http.put<ManagerLeave>(
      `${API_BASE}/api/manager/${managerId}/leaves/${leaveId}/reject`,
      { reason: reason ?? '' }
    );
  }
}

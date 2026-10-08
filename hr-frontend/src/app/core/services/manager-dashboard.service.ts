import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ManagerDashboardStatsDTO } from '../models/interfaces';
import { environment } from '../../../environments/environment';

export interface ManagerNotification {
  id?: number;
  text: string;
  time: string;
  icon: string;
  unread: boolean;
  type?: string;
  link?: string;
}

export interface EmployeeSummary {
  id: number;
  firstName: string;
  lastName: string;
  position: string;
  photoUrl?: string;
}

export interface ManagerDashboardData {
  totalEmployees: number;
  activeEmployees: number;
  pendingLeaves: number;
  enrolledEmployeesCount: number;
  activeTrainings: number;
  completedTrainings: number;
  averageProgression: number;
  topTrainings: { [key: string]: number };
  teamMembers: EmployeeSummary[];
  recentNotifications: ManagerNotification[];
}

const API_BASE = `${environment.apiUrl}`;

@Injectable({ providedIn: 'root' })
export class ManagerDashboardService {

  constructor(private http: HttpClient) {}

  /**
   * Appelle GET /api/manager/dashboard/{managerId}
   * et retourne le DTO complet du dashboard manager.
   */
  getDashboard(managerId: number): Observable<ManagerDashboardData> {
    return this.http.get<ManagerDashboardData>(
      `${API_BASE}/api/manager/dashboard/${managerId}`
    );
  }

  /**
   * Appelle GET /api/manager/dashboard/stats
   * et retourne les statistiques RH / recrutement du manager.
   */
  getManagerDashboardStats(): Observable<ManagerDashboardStatsDTO> {
    return this.http.get<ManagerDashboardStatsDTO>(
      `${API_BASE}/api/manager/dashboard/stats`
    );
  }
}

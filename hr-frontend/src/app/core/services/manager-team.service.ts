import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Modèle d'un membre de l'équipe (ManagerTeamMemberDTO) */
export interface TeamMember {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  position?: string;
  departmentName?: string;
  department?: string;
  hireDate?: string;
  status?: 'ACTIVE' | 'INACTIVE' | 'ON_LEAVE' | string;
  photo?: string;
  photoUrl?: string;
  salary?: number;
  availableLeaveDays?: number;
  address?: string;
  isOnLeaveToday?: boolean;
}

const API_BASE = 'http://localhost:8087';

@Injectable({ providedIn: 'root' })
export class ManagerTeamService {

  constructor(private http: HttpClient) {}

  /**
   * Appelle GET /api/manager/team pour récupérer l'équipe réelle du manager connecté depuis la BDD.
   */
  getManagerTeam(): Observable<TeamMember[]> {
    return this.http.get<TeamMember[]>(`${API_BASE}/api/manager/team`);
  }

  /**
   * Appelle GET /api/manager/team/{employeeId} pour le détail d'un membre.
   */
  getTeamMemberDetail(employeeId: number): Observable<TeamMember> {
    return this.http.get<TeamMember>(`${API_BASE}/api/manager/team/${employeeId}`);
  }

  /** URL de la photo d'un employé */
  getPhotoUrl(employeeId: number): string {
    return `${API_BASE}/api/employees/${employeeId}/photo`;
  }
}

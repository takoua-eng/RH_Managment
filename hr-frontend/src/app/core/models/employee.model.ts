export interface Employee {
  id: number;
  firstName: string;
  lastName: string;
  name: string; // legacy support (non-optional to match interfaces.ts)
  email: string;
  phone: string;
  position: string;
  department: string;
  hireDate: string;
  status: 'ACTIVE' | 'INACTIVE' | 'Actif' | 'Congé' | 'Suspendu';
  role?: 'ADMIN' | 'RH' | 'MANAGER' | 'EMPLOYE' | 'EMPLOYEE' | string;
  keycloakId?: string;
  photoContentType?: string;
  photoUrl?: string;
  salary: number; // legacy support (non-optional to match interfaces.ts)
  photo: string; // legacy support (non-optional to match interfaces.ts)
  address?: string;
  availableLeaveDays?: number;
  managerId?: number | null;
  managerName?: string | null;
  // ===== Statut "En congé" (calculé par le backend, jamais envoyé) =====
  onLeaveToday?: boolean;          // vrai si un congé approuvé couvre aujourd'hui
  leaveReturnDate?: string | null; // date de retour (lendemain de la fin du congé)
}

export interface EmployeeRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  position: string;
  department: string;
  hireDate: string;
  salary?: number;
  status?: string;
  role?: string;
  address?: string;
  availableLeaveDays?: number;
  managerId?: number | null;
}
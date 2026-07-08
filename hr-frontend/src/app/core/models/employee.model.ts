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
  keycloakId?: string;
  photoContentType?: string;
  photoUrl?: string;
  salary: number; // legacy support (non-optional to match interfaces.ts)
  photo: string; // legacy support (non-optional to match interfaces.ts)
}

export interface EmployeeRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  position: string;
  department: string;
  hireDate: string;
  salary: number;
}

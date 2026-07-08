export interface Employee {
  id: number;
  firstName: string;
  lastName: string;
  name: string;
  email: string;
  phone: string;
  position: string;
  department: string;
  hireDate: string;
  status: 'ACTIVE' | 'INACTIVE' | 'Actif' | 'Congé' | 'Suspendu';
  keycloakId?: string;
  photoContentType?: string;
  photoUrl?: string;
  salary: number;
  photo: string;
}

export interface Department {
  name: string;
  employeesCount: number;
  manager: string;
  budget: string;
  description: string;
}

export interface Candidate {
  id: number;
  name: string;
  cvName: string;
  position: string;
  aiScore: number;
  status: 'Nouveau' | 'Entretien' | 'Embauché' | 'Rejeté';
  matchPercent: number;
  skills: string[];
  strengths: string[];
  weaknesses: string[];
}

export interface LeaveRequest {
  id: number;
  employeeName: string;
  startDate: string;
  endDate: string;
  type: 'Payé' | 'Maladie' | 'Sans solde' | 'RTT';
  status: 'Approuvé' | 'En attente' | 'Refusé';
  reason?: string;
}

export interface Training {
  name: string;
  progression: number;
  description: string;
  status: 'En cours' | 'Terminé' | 'Non commencé';
}

export interface HrDocument {
  id: number;
  name: string;
  type: 'Contrat' | 'Fiche de paie' | 'Diplôme' | 'Attestation';
  dateUploaded: string;
  size: string;
  url: string;
}

export interface Evaluation {
  id: number;
  employeeName: string;
  date: string;
  communication: number;
  leadership: number;
  technical: number;
  teamwork: number;
  productivity: number;
  comments: string;
}

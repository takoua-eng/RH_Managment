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
  role?: 'ADMIN' | 'RH' | 'MANAGER' | 'EMPLOYE' | 'EMPLOYEE' | string;
  keycloakId?: string;
  photoContentType?: string;
  photoUrl?: string;
  salary: number;
  photo: string;
  address?: string;
  availableLeaveDays?: number;
  managerId?: number | null;
  managerName?: string | null;
  // ===== Statut "En congé" (calculé par le backend, jamais envoyé) =====
  onLeaveToday?: boolean;          // vrai si un congé approuvé couvre aujourd'hui
  leaveReturnDate?: string | null; // date de retour (lendemain de la fin du congé)
}

export interface EmployeeSummary {
  id: number;
  firstName: string;
  lastName: string;
  position: string;
  photoUrl: string;
}

export interface Department {
  id: number;
  name: string;
  description: string;
  manager: EmployeeSummary | null;
  employeeCount: number;
  location?: string;
  budget?: number;
  managerName?: string;
  managerId?: number | null;
}

export interface DepartmentRequest {
  name: string;
  description: string;
  managerId: number | null;
  location?: string;
  budget?: number;
}

/*export interface Department {
  name: string;
  employeesCount: number;
  manager: string;
  budget: string;
  description: string;
}*/

export interface MockCandidate {
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

export interface AiAnalysisProfile {
  competences?: string[];
  annees_experience?: number;
  niveau_etudes?: number;
  langues?: string[];
}

export interface AiAnalysis {
  score?: number;
  recommandation?: 'COMPATIBLE' | 'A_EXAMINER' | 'NON_COMPATIBLE' | string;
  methode?: string;
  profil?: AiAnalysisProfile;
  competences_trouvees?: string[];
  competences_manquantes?: string[];
  points_forts?: string[];
  points_faibles?: string[];
}

export interface Candidate {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  address: string;
  education: string;
  experience: string;
  skills: string;
  applicationDate: string;
  status: string;
  jobOfferId: number;
  jobOfferTitle: string;
  jobOfferDepartment: string;
  hasCv: boolean;
  cvFileName: string;
  hasMotivationLetter: boolean;
  motivationLetterFileName: string;
  assignedManagerId?: number;
  assignedManagerName?: string;
  transmissionDate?: string;
  aiScore?: number | null;
  aiRecommendation?: 'COMPATIBLE' | 'A_EXAMINER' | 'NON_COMPATIBLE' | string | null;
  aiStatus?: 'EN_ATTENTE' | 'TERMINEE' | 'ERREUR' | string | null;
  aiError?: string | null;
  aiAnalysis?: AiAnalysis | null;
}

// ===== Congés =====
export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export const LEAVE_STATUS: Record<LeaveStatus, { label: string; css: string }> = {
  PENDING:   { label: 'En attente', css: 'badge-orange' },
  APPROVED:  { label: 'Approuvé',   css: 'badge-vert' },
  REJECTED:  { label: 'Refusé',     css: 'badge-rouge' },
  CANCELLED: { label: 'Annulé',     css: 'badge-gris' },
};

export type LeaveType = 'ANNUAL' | 'SICK' | 'OTHER';

export const LEAVE_TYPE: Record<LeaveType, string> = {
  ANNUAL: 'Congé annuel',
  SICK:   'Maladie',
  OTHER:  'Autre',
};

export interface LeaveRequest {
  id: number;
  employeeId?: number;
  employeeName: string;
  startDate: string;
  endDate: string;
  type: LeaveType | string;
  status: LeaveStatus;
  reason?: string;
  // Décision du manager
  decidedById?: number | null;
  decidedAt?: string | null;
  decisionComment?: string | null;
}

export interface Training {
  id: number;
  title: string;
  description: string;
  trainer: string;
  startDate: string;
  duration: string;
  location: string;
  syllabus?: string;
  name?: string;
  progression?: number;
  status?: string;
  endDate?: string;
  category?: string;
  level?: string;
  mode?: string;
  availableSeats?: number;
  objectives?: string;
}

export interface TrainingEnrollment {
  id: number;
  employeeId: number;
  employeeName: string;
  employeePosition?: string;
  training: Training;
  enrollmentDate: string;
  status: 'À venir' | 'En cours' | 'Terminée' | 'Annulée' | 'INSCRIT' | 'Indisponible';
  progression: number;
  hasCertificate: boolean;
}

export interface HrDocument {
  id: number;
  name: string;
  type: 'Contrat' | 'Fiche de paie' | 'Diplôme' | 'Attestation' | 'Autre';
  dateUploaded?: string;
  uploadDate?: string;
  size: string | number;
  url?: string;
  employeeId?: number;
  employeeName?: string;
}

export interface Evaluation {
  id: number;
  employeeName: string;
  employeeId?: number;
  managerId?: number;
  managerName?: string;
  date: string;
  communication: number;
  leadership: number;
  technical: number;
  teamwork: number;
  productivity: number;
  comments: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface NotificationItem {
  id: number;
  text: string;
  time: string;
  icon: string;
  unread: boolean;
  type?: string;
  link?: string;
}

export interface JobOffer {
  id?: number;
  title: string;
  description: string;
  department: string;
  contractType: string;
  experienceLevel: string;
  requiredSkills: string;
  location: string;
  salaryRange: string;
  publicationDate?: string;
  applicationDeadline: string;
  numberOfPositions: number;
  status: 'DRAFT' | 'PUBLISHED' | 'CLOSED';
}

export type InterviewType = 'PRESENTIEL' | 'VISIO' | 'TELEPHONE';
export type InterviewStatus = 'PLANIFIE' | 'TERMINE' | 'ANNULE';
export type ManagerAvisType = 'FAVORABLE' | 'RESERVE' | 'DEFAVORABLE';

export interface AiQuestion {
  categorie?: string;
  question: string;
  objectif?: string;
  a_ecouter?: string[];
}

export interface AiQuestions {
  duree_estimee_min?: number;
  questions?: AiQuestion[];
}

export interface NoteQuestion {
  index: number;
  posee: boolean;
  notes: string;
}

export interface InterviewNotes {
  questions: NoteQuestion[];
}

export interface InterviewRequest {
  candidateId: number;
  interviewDate: string;
  interviewTime: string;
  durationMinutes: number;
  type: InterviewType;
  locationOrLink?: string;
  comment?: string;
}

export interface Interview {
  id: number;
  candidateId: number;
  candidateFirstName?: string;
  candidateLastName?: string;
  candidateEmail?: string;
  jobOfferId: number;
  jobOfferTitle?: string;
  department?: string;
  managerId: number;
  managerName?: string;
  interviewDate: string;
  interviewTime: string;
  durationMinutes: number;
  type: InterviewType;
  locationOrLink?: string;
  comment?: string;
  status: InterviewStatus;
  createdAt?: string;
  updatedAt?: string;
  modificationCount?: number;
  emailSent?: boolean;
  emailSentAt?: string;
  cancelledAt?: string;
  cancellationReason?: string;

  // ===== Questions IA & Avis Manager =====
  aiQuestionsStatus?: 'EN_ATTENTE' | 'TERMINEE' | 'ERREUR' | string | null;
  aiQuestionsError?: string | null;
  aiQuestions?: AiQuestions | string | null;
  interviewNotes?: InterviewNotes | string | null;
  managerRecommendation?: ManagerAvisType | string | null;
  managerRating?: number | null;
  managerFeedback?: string | null;
  feedbackSubmittedAt?: string | null;
}

// ===== Dashboard Stats DTO Interfaces =====
export interface KpiStats {
  totalEmployees: number;
  pendingLeaves: number;
  activeJobOffers: number;
  applicationsThisMonth: number;
  applicationsLastMonth: number;
  interviewsThisWeek: number;
  pendingManagerFeedbacks: number;
  averageAiScore: number | null;
  averageTimeToDecisionDays: number | null;
}

export interface MonthlyTrend {
  month: string;
  label: string;
  applications: number;
  hired: number;
}

export interface FunnelStats {
  received: number;
  aiAnalyzed: number;
  aiCompatible: number;
  transmittedToManager: number;
  interviewScheduled: number;
  interviewEvaluated: number;
  accepted: number;
}

export interface AiRecommendationStats {
  compatible: number;
  aExaminer: number;
  nonCompatible: number;
  erreur: number;
  enAttente: number;
}

export interface ScoreDistributionBucket {
  range: string;
  count: number;
}

export interface MissingSkillCount {
  skill: string;
  count: number;
}

export interface AgreementMatrix {
  compatibleFavorable: number;
  compatibleReserve: number;
  compatibleDefavorable: number;
  aExaminerFavorable: number;
  aExaminerReserve: number;
  aExaminerDefavorable: number;
  nonCompatibleFavorable: number;
  nonCompatibleReserve: number;
  nonCompatibleDefavorable: number;
}

export interface AiManagerAgreementStats {
  matrix: AgreementMatrix;
  agreementRate: number | null;
  totalPairs: number;
}

export interface UnprocessedApplicationDto {
  id: number;
  name: string;
  jobTitle: string;
  daysPending: number;
}

export interface AiErrorDto {
  id: number;
  name: string;
  jobTitle: string;
  aiError: string;
}

export interface MissingFeedbackDto {
  interviewId: number;
  candidateName: string;
  managerName: string;
  interviewDate: string;
}

export interface ExpiringOfferDto {
  offerId: number;
  title: string;
  applicationDeadline: string;
}

export interface ActionsRequiredStats {
  unprocessedApplications: UnprocessedApplicationDto[];
  totalUnprocessedCount: number;
  aiErrors: AiErrorDto[];
  totalAiErrorsCount: number;
  missingFeedbacks: MissingFeedbackDto[];
  totalMissingFeedbacksCount: number;
  expiringOffers: ExpiringOfferDto[];
  totalExpiringOffersCount: number;
}

export interface UpcomingInterviewDto {
  id: number;
  interviewDate: string;
  interviewTime: string;
  candidateName: string;
  jobTitle: string;
  managerName: string;
  aiQuestionsStatus: string;
}

export interface TopOfferDto {
  id: number;
  title: string;
  department: string;
  applicationsCount: number;
  averageAiScore: number | null;
  bestCandidateName: string | null;
  bestCandidateScore: number | null;
}

export interface DepartmentLeavesDto {
  departmentName: string;
  leavesCount: number;
}

export interface AiServiceStats {
  online: boolean;
  methode: string;
  checkedAt: string;
  lastAnalysisAt: string | null;
  analysesLast30Days: number;
}

export interface DashboardStatsDTO {
  kpis: KpiStats;
  monthlyTrend: MonthlyTrend[];
  funnel: FunnelStats;
  aiRecommendations: AiRecommendationStats;
  aiScoreDistribution: ScoreDistributionBucket[];
  topMissingSkills: MissingSkillCount[];
  aiManagerAgreement: AiManagerAgreementStats;
  actionsRequired: ActionsRequiredStats;
  upcomingInterviews: UpcomingInterviewDto[];
  topOffers: TopOfferDto[];
  leavesByDepartment: DepartmentLeavesDto[];
  aiService: AiServiceStats;
}

// ===== Manager Dashboard Stats DTO Interfaces =====
export interface ManagerKpiStats {
  candidatesToProcess: number;
  interviewsThisWeek: number;
  pendingFeedbacks: number;
  averageAiScoreOfTransmitted: number | null;
}

export interface ManagerInterviewDetailsDto {
  interviewId: number;
  candidateId: number;
  candidateName: string;
  jobTitle: string;
  date: string;
  time: string;
  durationMinutes: number;
  type: InterviewType;
  locationOrLink?: string | null;
  aiQuestionsStatus?: string | null;
  candidateAiScore?: number | null;
  candidateAiRecommendation?: string | null;
}

export interface ManagerCandidateToProcessDto {
  candidateId: number;
  name: string;
  jobTitle: string;
  applicationDate: string;
  aiScore: number | null;
  aiRecommendation: string | null;
  aiStatus: string | null;
}

export interface ManagerPendingFeedbackDto {
  interviewId: number;
  candidateName: string;
  jobTitle: string;
  date: string;
  daysSince: number;
}

export interface ManagerMyEvaluationsStats {
  favorable: number;
  reserve: number;
  defavorable: number;
  totalEvaluated: number;
  averageRating: number | null;
  agreementWithAi: number | null;
}

export interface AbsentTodayDto {
  name: string;
  returnDate: string;
}

export interface UpcomingLeaveDto {
  name: string;
  startDate: string;
  endDate: string;
}

export interface TeamAbsencesStats {
  absentToday: AbsentTodayDto[];
  upcomingLeaves: UpcomingLeaveDto[];
}

export interface ManagerDashboardStatsDTO {
  kpis: ManagerKpiStats;
  nextInterview: ManagerInterviewDetailsDto | null;
  upcomingInterviews: ManagerInterviewDetailsDto[];
  candidatesToProcess: ManagerCandidateToProcessDto[];
  pendingFeedbacks: ManagerPendingFeedbackDto[];
  myEvaluations: ManagerMyEvaluationsStats;
  teamAbsences: TeamAbsencesStats;
}


// ===== Employee Dashboard (espace employé) =====
export interface EmployeeDashboardLeave {
  id: number;
  type: string;
  startDate: string;
  endDate: string;
  daysCount: number;
  status: LeaveStatus;
  reason: string | null;
  decisionComment: string | null;
  decidedAt: string | null;
  decidedByName: string | null;
}

export interface EmployeeDashboardProfile {
  id: number;
  firstName: string;
  lastName: string;
  position: string | null;
  departmentName: string | null;
  hireDate: string | null;
  seniorityText: string | null;
  availableLeaveDays: number | null;
}

export interface EmployeeDashboardLeaveStatus {
  onLeaveToday: boolean;
  currentLeaveEnd: string | null;
  nextApprovedLeave: EmployeeDashboardLeave | null;
  daysUntilNextLeave: number | null;
  pendingRequests: number;
  daysTakenThisYear: number;
}

export interface EmployeeDashboardManager {
  id: number;
  firstName: string;
  lastName: string;
  position: string | null;
  email: string | null;
}

export interface EmployeeDashboard {
  profile: EmployeeDashboardProfile | null;
  leaveStatus: EmployeeDashboardLeaveStatus | null;
  recentLeaves: EmployeeDashboardLeave[];
  manager: EmployeeDashboardManager | null;
  teamAbsences: { name: string; startDate: string; endDate: string; today: boolean }[];
  trainings: { inProgress: { title: string; progression: number | null }[]; completedCount: number };
  monthLeaves: { startDate: string; endDate: string; status: string }[];
  notifications: { id: number; text: string; type: string | null; unread: boolean; createdAt: string }[];
}
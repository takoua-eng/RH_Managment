import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CandidateService } from '../../core/services/candidate.service';
import { JobOfferService } from '../../core/services/job-offer.service';
import { DepartmentService } from '../../core/services/department.service';
import { InterviewService } from '../../core/services/interview.service';
import { Candidate, Department, Interview } from '../../core/models/interfaces';

@Component({
  selector: 'app-recruitment-tracking',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './recruitment-tracking.component.html',
  styleUrl: './recruitment-tracking.component.scss'
})
export class RecruitmentTrackingComponent implements OnInit {
  candidates: Candidate[] = [];
  filteredCandidates: Candidate[] = [];
  jobOffers: any[] = [];
  departments: Department[] = [];
  candidateInterviewsMap: { [candidateId: number]: Interview[] } = {};
  loading = true;

  // Filters
  searchQuery = '';
  statusFilter = '';
  offerFilter = '';
  departmentFilter = '';
  startDateFilter = '';
  endDateFilter = '';

  // History Modal
  selectedCandidateForHistory: Candidate | null = null;
  selectedCandidateInterviews: Interview[] = [];
  showHistoryModal = false;
  loadingHistoryInterviews = false;

  constructor(
    private candidateService: CandidateService,
    private jobOfferService: JobOfferService,
    private departmentService: DepartmentService,
    private interviewService: InterviewService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadDepartments();
    this.loadJobOffers();
    this.loadCandidates();
  }

  loadDepartments(): void {
    this.departmentService.getAll().subscribe({
      next: (data) => this.departments = data,
      error: (err) => console.error('Erreur chargement départements:', err)
    });
  }

  loadJobOffers(): void {
    this.jobOfferService.getAllOffers().subscribe({
      next: (data) => this.jobOffers = data,
      error: (err) => console.error('Erreur chargement offres:', err)
    });
  }

  loadCandidates(): void {
    this.loading = true;
    this.candidateService.getAllCandidates().subscribe({
      next: (data) => {
        this.candidates = data;
        this.loadInterviewsForCandidates();
        this.applyFilters();
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement candidatures:', err);
        this.loading = false;
      }
    });
  }

  loadInterviewsForCandidates(): void {
    // For each candidate with status ENTRETIEN_PLANIFIE, ENTRETIEN_TERMINE, ACCEPTEE, REFUSEE
    this.candidates.forEach(c => {
      if (['ENTRETIEN_PLANIFIE', 'ENTRETIEN_TERMINE', 'ACCEPTEE', 'REFUSEE'].includes(c.status)) {
        this.interviewService.getInterviewsByCandidate(c.id).subscribe({
          next: (interviews) => {
            this.candidateInterviewsMap[c.id] = interviews;
          },
          error: (err) => console.error(`Erreur entretiens pour candidat ${c.id}:`, err)
        });
      }
    });
  }

  getInterviewDate(candidateId: number): string | null {
    const interviews = this.candidateInterviewsMap[candidateId];
    if (interviews && interviews.length > 0) {
      // Find latest interview date
      const latest = interviews[0];
      return `${latest.interviewDate} à ${latest.interviewTime}`;
    }
    return null;
  }

  applyFilters(): void {
    let result = this.candidates.filter(c => {
      const matchSearch = this.searchQuery ? 
        (c.firstName.toLowerCase().includes(this.searchQuery.toLowerCase()) || 
         c.lastName.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
         c.email.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
         (c.jobOfferTitle && c.jobOfferTitle.toLowerCase().includes(this.searchQuery.toLowerCase()))) : true;

      const matchStatus = this.statusFilter ? c.status === this.statusFilter : true;
      const matchOffer = this.offerFilter ? c.jobOfferId?.toString() === this.offerFilter : true;
      const matchDept = this.departmentFilter ? c.jobOfferDepartment === this.departmentFilter : true;

      let matchPeriod = true;
      if (c.applicationDate) {
        const appDate = new Date(c.applicationDate);
        if (this.startDateFilter) {
          const start = new Date(this.startDateFilter);
          if (appDate < start) matchPeriod = false;
        }
        if (this.endDateFilter) {
          const end = new Date(this.endDateFilter);
          end.setHours(23, 59, 59, 999);
          if (appDate > end) matchPeriod = false;
        }
      }

      return matchSearch && matchStatus && matchOffer && matchDept && matchPeriod;
    });

    // Default sort by application date desc
    result.sort((a, b) => new Date(b.applicationDate).getTime() - new Date(a.applicationDate).getTime());

    this.filteredCandidates = result;
  }

  resetFilters(): void {
    this.searchQuery = '';
    this.statusFilter = '';
    this.offerFilter = '';
    this.departmentFilter = '';
    this.startDateFilter = '';
    this.endDateFilter = '';
    this.applyFilters();
  }

  openHistoryModal(candidate: Candidate): void {
    this.selectedCandidateForHistory = candidate;
    this.selectedCandidateInterviews = [];
    this.showHistoryModal = true;
    this.loadingHistoryInterviews = true;

    this.interviewService.getInterviewsByCandidate(candidate.id).subscribe({
      next: (interviews) => {
        this.selectedCandidateInterviews = interviews;
        this.loadingHistoryInterviews = false;
      },
      error: (err) => {
        console.error('Erreur chargement historique entretiens:', err);
        this.loadingHistoryInterviews = false;
      }
    });
  }

  closeHistoryModal(): void {
    this.showHistoryModal = false;
    this.selectedCandidateForHistory = null;
    this.selectedCandidateInterviews = [];
  }

  getStatusBadgeClass(status: string): string {
    switch (status) {
      case 'NOUVELLE': return 'bg-info-subtle text-info border border-info';
      case 'TRANSMISE_MANAGER': return 'bg-primary-subtle text-primary border border-primary';
      case 'ENTRETIEN_PLANIFIE': return 'bg-warning-subtle text-warning border border-warning';
      case 'ENTRETIEN_TERMINE': return 'bg-indigo-subtle text-indigo border border-indigo';
      case 'ACCEPTEE': return 'bg-success-subtle text-success border border-success';
      case 'REFUSEE': return 'bg-danger-subtle text-danger border border-danger';
      default: return 'bg-secondary text-white';
    }
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'NOUVELLE': return 'Nouvelle';
      case 'TRANSMISE_MANAGER': return 'Transmise au Manager';
      case 'ENTRETIEN_PLANIFIE': return 'Entretien Planifié';
      case 'ENTRETIEN_TERMINE': return 'Entretien Terminé';
      case 'ACCEPTEE': return 'Acceptée';
      case 'REFUSEE': return 'Refusée';
      default: return status;
    }
  }

  getStageIndex(status: string): number {
    switch (status) {
      case 'NOUVELLE': return 1;
      case 'TRANSMISE_MANAGER': return 2;
      case 'ENTRETIEN_PLANIFIE': return 3;
      case 'ENTRETIEN_TERMINE': return 4;
      case 'ACCEPTEE': return 5;
      case 'REFUSEE': return 5;
      default: return 1;
    }
  }

  // Count metrics for quick overview
  get countNew(): number { return this.candidates.filter(c => c.status === 'NOUVELLE').length; }
  get countTransmitted(): number { return this.candidates.filter(c => c.status === 'TRANSMISE_MANAGER').length; }
  get countInterview(): number { return this.candidates.filter(c => c.status === 'ENTRETIEN_PLANIFIE' || c.status === 'ENTRETIEN_TERMINE').length; }
  get countAccepted(): number { return this.candidates.filter(c => c.status === 'ACCEPTEE').length; }
  get countRejected(): number { return this.candidates.filter(c => c.status === 'REFUSEE').length; }

  goToDetails(candidateId: number): void {
    this.closeHistoryModal();
    this.router.navigate(['/dashboard/admin-candidates', candidateId]);
  }
}

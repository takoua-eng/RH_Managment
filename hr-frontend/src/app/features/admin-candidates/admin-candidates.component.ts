import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CandidateService } from '../../core/services/candidate.service';
import { JobOfferService } from '../../core/services/job-offer.service';
import { DepartmentService } from '../../core/services/department.service';
import { EmployeeService } from '../../core/services/employee.service';
import { Candidate, Department, Employee } from '../../core/models/interfaces';
import { ToastNotificationService } from '../../core/services/toast-notification.service';

@Component({
  selector: 'app-admin-candidates',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-candidates.component.html',
  styleUrl: './admin-candidates.component.scss'
})
export class AdminCandidatesComponent implements OnInit {
  candidates: Candidate[] = [];
  filteredCandidates: Candidate[] = [];
  jobOffers: any[] = [];
  departments: Department[] = [];
  managers: Employee[] = [];
  loading = true;

  // Filters
  searchQuery = '';
  statusFilter = '';
  offerFilter = '';
  departmentFilter = '';
  aiRecommendationFilter = '';
  sortBy: 'date' | 'aiScore' = 'date';
  sortOrder: 'asc' | 'desc' = 'desc';

  // Status Modal
  selectedCandidate: Candidate | null = null;
  newStatus = '';
  
  // Transmit Modal
  showTransmitModal = false;
  selectedManagerId: number | null = null;

  constructor(
    private candidateService: CandidateService,
    private jobOfferService: JobOfferService,
    private departmentService: DepartmentService,
    private employeeService: EmployeeService,
    private toastService: ToastNotificationService,
    public router: Router
  ) {}

  ngOnInit(): void {
    this.loadDepartments();
    this.loadJobOffers();
    this.loadCandidates();
    this.loadManagers();
  }

  loadManagers(): void {
    // Assuming EmployeeService has getManagers() which returns Observable<Employee[]>
    this.employeeService.getManagers().subscribe({
      next: (data: any) => this.managers = data,
      error: (err: any) => console.error(err)
    });
  }

  loadDepartments(): void {
    this.departmentService.getAll().subscribe({
      next: (data: any) => this.departments = data,
      error: (err: any) => console.error(err)
    });
  }

  getManagerName(departmentName: string): string {
    const dept = this.departments.find(d => d.name === departmentName);
    if (dept && dept.manager) {
      return `${dept.manager.firstName} ${dept.manager.lastName}`;
    }
    return 'Non assigné';
  }

  loadJobOffers(): void {
    this.jobOfferService.getAllOffers().subscribe({
      next: (data: any) => this.jobOffers = data,
      error: (err: any) => console.error(err)
    });
  }

  loadCandidates(): void {
    this.loading = true;
    this.candidateService.getAllCandidates().subscribe({
      next: (data: any) => {
        this.candidates = data;
        this.applyFilters();
        this.loading = false;
      },
      error: (err: any) => {
        this.toastService.error("Erreur lors du chargement des candidatures.");
        this.loading = false;
        console.error(err);
      }
    });
  }

  applyFilters(): void {
    let result = this.candidates.filter(c => {
      const matchSearch = this.searchQuery ? 
        (c.firstName.toLowerCase().includes(this.searchQuery.toLowerCase()) || 
         c.lastName.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
         c.email.toLowerCase().includes(this.searchQuery.toLowerCase())) : true;
         
      const matchStatus = this.statusFilter ? c.status === this.statusFilter : true;
      const matchOffer = this.offerFilter ? c.jobOfferId.toString() === this.offerFilter : true;
      const matchDept = this.departmentFilter ? c.jobOfferDepartment === this.departmentFilter : true;
      const matchAiRec = this.aiRecommendationFilter ? c.aiRecommendation === this.aiRecommendationFilter : true;
      
      return matchSearch && matchStatus && matchOffer && matchDept && matchAiRec;
    });

    result.sort((a, b) => {
      if (this.sortBy === 'aiScore') {
        const scoreA = (a.aiStatus === 'TERMINEE' && a.aiScore != null) ? a.aiScore : -1;
        const scoreB = (b.aiStatus === 'TERMINEE' && b.aiScore != null) ? b.aiScore : -1;
        if (scoreA === -1 && scoreB === -1) return 0;
        if (scoreA === -1) return 1;
        if (scoreB === -1) return -1;
        return this.sortOrder === 'desc' ? scoreB - scoreA : scoreA - scoreB;
      } else {
        const dateA = new Date(a.applicationDate).getTime();
        const dateB = new Date(b.applicationDate).getTime();
        return this.sortOrder === 'desc' ? dateB - dateA : dateA - dateB;
      }
    });

    this.filteredCandidates = result;
  }

  toggleSort(): void {
    if (this.sortBy === 'date') {
      this.sortOrder = this.sortOrder === 'desc' ? 'asc' : 'desc';
    } else {
      this.sortBy = 'date';
      this.sortOrder = 'desc';
    }
    this.applyFilters();
  }

  toggleAiScoreSort(): void {
    if (this.sortBy === 'aiScore') {
      this.sortOrder = this.sortOrder === 'desc' ? 'asc' : 'desc';
    } else {
      this.sortBy = 'aiScore';
      this.sortOrder = 'desc';
    }
    this.applyFilters();
  }

  formatAiScore(score?: number | null): string {
    if (score == null) return '';
    return `${Math.round(score * 100)} %`;
  }

  getAiScoreBadgeClass(recommendation?: string | null): string {
    switch (recommendation) {
      case 'COMPATIBLE': return 'bg-success-subtle text-success border border-success-subtle';
      case 'A_EXAMINER': return 'bg-warning-subtle text-warning-emphasis border border-warning-subtle';
      case 'NON_COMPATIBLE': return 'bg-danger-subtle text-danger border border-danger-subtle';
      default: return 'bg-secondary-subtle text-secondary border border-secondary-subtle';
    }
  }

  getStatusBadgeClass(status: string): string {
    switch (status) {
      case 'NOUVELLE': return 'badge-new';
      case 'TRANSMISE_MANAGER': return 'badge-forwarded';
      case 'ENTRETIEN_PLANIFIE': return 'badge-interview';
      case 'ENTRETIEN_TERMINE': return 'badge-interview-done';
      case 'ACCEPTEE': return 'badge-accepted';
      case 'REFUSEE': return 'badge-rejected';
      default: return 'bg-secondary';
    }
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'NOUVELLE': return 'Nouvelle';
      case 'TRANSMISE_MANAGER': return 'Transmise Manager';
      case 'ENTRETIEN_PLANIFIE': return 'Entretien planifié';
      case 'ENTRETIEN_TERMINE': return 'Entretien terminé';
      case 'ACCEPTEE': return 'Acceptée';
      case 'REFUSEE': return 'Refusée';
      default: return status;
    }
  }

  openStatusModal(candidate: Candidate): void {
    this.selectedCandidate = candidate;
    this.newStatus = candidate.status;
  }

  closeStatusModal(): void {
    this.selectedCandidate = null;
  }

  saveStatus(): void {
    if (!this.selectedCandidate || !this.newStatus) return;

    this.candidateService.updateStatus(this.selectedCandidate.id, this.newStatus).subscribe({
      next: (updated) => {
        this.toastService.success("Statut mis à jour avec succès.");
        const index = this.candidates.findIndex(c => c.id === updated.id);
        if (index !== -1) {
          this.candidates[index] = updated;
          this.applyFilters();
        }
        this.closeStatusModal();
      },
      error: (err: any) => {
        this.toastService.error("Erreur lors de la mise à jour.");
        console.error(err);
      }
    });
  }

  openTransmitModal(candidate: Candidate): void {
    this.selectedCandidate = candidate;
    this.selectedManagerId = null;
    this.showTransmitModal = true;
  }

  closeTransmitModal(): void {
    this.showTransmitModal = false;
    this.selectedCandidate = null;
    this.selectedManagerId = null;
  }

  confirmTransmit(): void {
    if (!this.selectedCandidate || !this.selectedManagerId) return;

    this.candidateService.transmitToManager(this.selectedCandidate.id, this.selectedManagerId).subscribe({
      next: (updated) => {
        this.toastService.success("Candidature transmise au manager avec succès.");
        const index = this.candidates.findIndex(c => c.id === updated.id);
        if (index !== -1) {
          this.candidates[index] = updated;
          this.applyFilters();
        }
        this.closeTransmitModal();
      },
      error: (err: any) => {
        this.toastService.error("Erreur lors de la transmission.");
        console.error(err);
      }
    });
  }

  goToDetails(id: number): void {
    this.router.navigate(['/dashboard/admin-candidates', id]);
  }

  downloadDocument(id: number, type: 'cv' | 'ml', fileName: string): void {
    const request = type === 'cv' ? 
      this.candidateService.downloadCv(id) : 
      this.candidateService.downloadMotivationLetter(id);

    request.subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        a.click();
        window.URL.revokeObjectURL(url);
        this.toastService.info(`Téléchargement de ${fileName} démarré.`);
      },
      error: (err: any) => {
        this.toastService.error("Le fichier n'a pas pu être téléchargé.");
        console.error(err);
      }
    });
  }
}

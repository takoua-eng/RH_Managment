import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CandidateService } from '../../core/services/candidate.service';
import { Candidate } from '../../core/models/interfaces';

@Component({
  selector: 'app-manager-recruitment',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './manager-recruitment.component.html',
  styleUrl: './manager-recruitment.component.scss'
})
export class ManagerRecruitmentComponent implements OnInit {
  candidates: Candidate[] = [];
  filteredCandidates: Candidate[] = [];
  loading = true;

  // Filters & Sorting
  searchQuery = '';
  statusFilter = '';
  sortBy: 'transmissionDate' | 'aiScore' = 'transmissionDate';
  sortOrder: 'asc' | 'desc' = 'desc';

  constructor(
    private candidateService: CandidateService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadAssignedCandidates();
  }

  loadAssignedCandidates(): void {
    this.loading = true;
    this.candidateService.getAssignedCandidates().subscribe({
      next: (data) => {
        this.candidates = data;
        this.applyFilters();
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement candidatures attribuées:', err);
        this.loading = false;
      }
    });
  }

  applyFilters(): void {
    let result = [...this.candidates];

    if (this.searchQuery.trim()) {
      const q = this.searchQuery.toLowerCase().trim();
      result = result.filter(c => 
        (c.firstName && c.firstName.toLowerCase().includes(q)) ||
        (c.lastName && c.lastName.toLowerCase().includes(q)) ||
        (c.email && c.email.toLowerCase().includes(q)) ||
        (c.jobOfferTitle && c.jobOfferTitle.toLowerCase().includes(q))
      );
    }

    if (this.statusFilter) {
      result = result.filter(c => c.status === this.statusFilter);
    }

    result.sort((a, b) => {
      if (this.sortBy === 'aiScore') {
        const scoreA = (a.aiStatus === 'TERMINEE' && a.aiScore != null) ? a.aiScore : -1;
        const scoreB = (b.aiStatus === 'TERMINEE' && b.aiScore != null) ? b.aiScore : -1;
        if (scoreA === -1 && scoreB === -1) return 0;
        if (scoreA === -1) return 1;
        if (scoreB === -1) return -1;
        return this.sortOrder === 'desc' ? scoreB - scoreA : scoreA - scoreB;
      } else {
        const dateA = new Date(a.transmissionDate || a.applicationDate || 0).getTime();
        const dateB = new Date(b.transmissionDate || b.applicationDate || 0).getTime();
        return this.sortOrder === 'desc' ? dateB - dateA : dateA - dateB;
      }
    });

    this.filteredCandidates = result;
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

  goToDetails(id: number): void {
    this.router.navigate(['/manager/recruitment', id]);
  }

  getStatusBadgeClass(status: string): string {
    switch (status) {
      case 'NOUVELLE': return 'bg-info text-dark';
      case 'TRANSMISE_MANAGER': return 'bg-primary text-white';
      case 'ENTRETIEN_PLANIFIE': return 'bg-warning text-dark';
      case 'ENTRETIEN_TERMINE': return 'bg-secondary text-white';
      case 'ACCEPTEE': return 'bg-success text-white';
      case 'REFUSEE': return 'bg-danger text-white';
      default: return 'bg-light text-dark';
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
}

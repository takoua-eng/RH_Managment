import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { InterviewService } from '../../core/services/interview.service';
import { Interview, InterviewStatus, InterviewType } from '../../core/models/interfaces';

@Component({
  selector: 'app-manager-interviews',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './manager-interviews.component.html',
  styleUrl: './manager-interviews.component.scss'
})
export class ManagerInterviewsComponent implements OnInit {
  interviews: Interview[] = [];
  filteredInterviews: Interview[] = [];
  loading = true;

  // Filters
  searchQuery = '';
  statusFilter = '';

  constructor(
    private interviewService: InterviewService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadMyInterviews();
  }

  loadMyInterviews(): void {
    this.loading = true;
    this.interviewService.getMyInterviews().subscribe({
      next: (data) => {
        this.interviews = data;
        this.applyFilters();
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement mes entretiens:', err);
        this.loading = false;
      }
    });
  }

  applyFilters(): void {
    let result = [...this.interviews];

    if (this.searchQuery.trim()) {
      const q = this.searchQuery.toLowerCase().trim();
      result = result.filter(it =>
        (it.candidateFirstName && it.candidateFirstName.toLowerCase().includes(q)) ||
        (it.candidateLastName && it.candidateLastName.toLowerCase().includes(q)) ||
        (it.jobOfferTitle && it.jobOfferTitle.toLowerCase().includes(q))
      );
    }

    if (this.statusFilter) {
      result = result.filter(it => it.status === this.statusFilter);
    }

    this.filteredInterviews = result;
  }

  openInterview(id: number): void {
    this.router.navigate(['/manager/interviews', id]);
  }

  getStatusBadgeClass(status: InterviewStatus): string {
    switch (status) {
      case 'PLANIFIE': return 'bg-warning text-dark';
      case 'TERMINE': return 'bg-success text-white';
      case 'ANNULE': return 'bg-danger text-white';
      default: return 'bg-secondary text-white';
    }
  }

  getStatusLabel(status: InterviewStatus): string {
    switch (status) {
      case 'PLANIFIE': return 'Planifié';
      case 'TERMINE': return 'Terminé';
      case 'ANNULE': return 'Annulé';
      default: return status;
    }
  }

  getRecommendationBadgeClass(rec?: string | null): string {
    switch (rec) {
      case 'FAVORABLE': return 'bg-success text-white';
      case 'RESERVE': return 'bg-warning text-dark';
      case 'DEFAVORABLE': return 'bg-danger text-white';
      default: return 'bg-secondary text-white';
    }
  }

  getRecommendationLabel(rec?: string | null): string {
    switch (rec) {
      case 'FAVORABLE': return 'Favorable';
      case 'RESERVE': return 'Réservé';
      case 'DEFAVORABLE': return 'Défavorable';
      default: return rec || '';
    }
  }

  getRecommendationIcon(rec?: string | null): string {
    switch (rec) {
      case 'FAVORABLE': return 'thumb_up';
      case 'RESERVE': return 'help_outline';
      case 'DEFAVORABLE': return 'thumb_down';
      default: return 'help';
    }
  }

  getInterviewTypeLabel(type: InterviewType): string {
    switch (type) {
      case 'PRESENTIEL': return 'Présentiel';
      case 'VISIO': return 'Visioconférence';
      case 'TELEPHONE': return 'Téléphonique';
      default: return type;
    }
  }
}

import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { CandidateService } from '../../../core/services/candidate.service';
import { InterviewService } from '../../../core/services/interview.service';
import { Candidate, Interview, InterviewRequest, InterviewType } from '../../../core/models/interfaces';
import { AiAnalysisCardComponent } from '../../../core/components/ai-analysis-card/ai-analysis-card.component';

@Component({
  selector: 'app-manager-candidate-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, AiAnalysisCardComponent],
  templateUrl: './manager-candidate-detail.component.html',
  styleUrl: './manager-candidate-detail.component.scss'
})
export class ManagerCandidateDetailComponent implements OnInit {
  candidate: Candidate | null = null;
  interviews: Interview[] = [];
  loading = true;
  submittingInterview = false;
  errorMessage = '';
  successMessage = '';

  // Modal Planification Entretien
  showInterviewModal = false;
  interviewForm: InterviewRequest = {
    candidateId: 0,
    interviewDate: '',
    interviewTime: '10:00',
    durationMinutes: 45,
    type: 'VISIO',
    locationOrLink: '',
    comment: ''
  };

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private candidateService: CandidateService,
    private interviewService: InterviewService
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadCandidate(+id);
      this.loadInterviews(+id);
    } else {
      this.goBack();
    }
  }

  loadCandidate(id: number): void {
    this.loading = true;
    this.candidateService.getCandidateById(id).subscribe({
      next: (data) => {
        this.candidate = data;
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement détails candidat:', err);
        if (err.status === 403) {
          this.errorMessage = "Accès refusé : vous n'avez pas l'autorisation d'accéder aux candidatures d'un autre département ou non attribuées.";
        } else {
          this.errorMessage = "Erreur lors du chargement de la candidature.";
        }
        this.loading = false;
      }
    });
  }

  loadInterviews(candidateId: number): void {
    this.interviewService.getInterviewsByCandidate(candidateId).subscribe({
      next: (data) => this.interviews = data,
      error: (err) => console.error('Erreur chargement entretiens:', err)
    });
  }

  isEditMode = false;
  selectedInterviewId: number | null = null;

  openInterviewModal(): void {
    if (!this.candidate) return;
    
    this.isEditMode = false;
    this.selectedInterviewId = null;

    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const dateStr = tomorrow.toISOString().split('T')[0];

    this.interviewForm = {
      candidateId: this.candidate.id,
      interviewDate: dateStr,
      interviewTime: '10:00',
      durationMinutes: 45,
      type: 'VISIO',
      locationOrLink: '',
      comment: ''
    };

    this.showInterviewModal = true;
  }

  openEditInterviewModal(it: Interview): void {
    if (!this.candidate) return;

    this.isEditMode = true;
    this.selectedInterviewId = it.id;

    this.interviewForm = {
      candidateId: this.candidate.id,
      interviewDate: it.interviewDate,
      interviewTime: it.interviewTime,
      durationMinutes: it.durationMinutes,
      type: it.type,
      locationOrLink: it.locationOrLink || '',
      comment: it.comment || ''
    };

    this.showInterviewModal = true;
  }

  closeInterviewModal(): void {
    this.showInterviewModal = false;
    this.isEditMode = false;
    this.selectedInterviewId = null;
  }

  submitInterview(): void {
    if (!this.candidate || !this.interviewForm.interviewDate || !this.interviewForm.interviewTime) return;

    this.submittingInterview = true;

    if (this.isEditMode && this.selectedInterviewId) {
      this.interviewService.updateInterview(this.selectedInterviewId, this.interviewForm).subscribe({
        next: (updatedInterview) => {
          this.submittingInterview = false;
          this.closeInterviewModal();
          this.successMessage = "Entretien mis à jour avec succès ! Un nouvel email d'actualisation a été envoyé au candidat.";
          const index = this.interviews.findIndex(i => i.id === updatedInterview.id);
          if (index !== -1) {
            this.interviews[index] = updatedInterview;
          }
          setTimeout(() => this.successMessage = '', 5000);
        },
        error: (err) => {
          console.error(err);
          this.submittingInterview = false;
          const msg = err.error?.message || "Erreur lors de la modification de l'entretien.";
          this.errorMessage = msg;
          alert(msg);
        }
      });
    } else {
      this.interviewService.scheduleInterview(this.interviewForm).subscribe({
        next: (newInterview) => {
          this.submittingInterview = false;
          this.closeInterviewModal();
          this.successMessage = "Entretien planifié avec succès ! Un email d'invitation a été envoyé au candidat.";
          if (this.candidate) {
            this.candidate.status = 'ENTRETIEN_PLANIFIE';
          }
          this.interviews.unshift(newInterview);
          setTimeout(() => this.successMessage = '', 5000);
        },
        error: (err) => {
          console.error(err);
          this.submittingInterview = false;
          const msg = err.error?.message || "Erreur lors de la planification de l'entretien.";
          this.errorMessage = msg;
          alert(msg);
        }
      });
    }
  }

  resendingEmailId: number | null = null;

  resendEmail(it: Interview): void {
    this.resendingEmailId = it.id;
    this.interviewService.resendInterviewEmail(it.id).subscribe({
      next: (updated) => {
        this.resendingEmailId = null;
        const index = this.interviews.findIndex(i => i.id === updated.id);
        if (index !== -1) {
          this.interviews[index] = updated;
        }
        this.successMessage = "Email de convocation réexpédié avec succès au candidat !";
        setTimeout(() => this.successMessage = '', 5000);
      },
      error: (err) => {
        console.error(err);
        this.resendingEmailId = null;
        const msg = err.error?.message || "Erreur lors de l'envoi de l'email.";
        this.errorMessage = msg;
        alert(msg);
      }
    });
  }

  showCancelModal = false;
  cancellingInterview: Interview | null = null;
  cancellationReasonInput = '';
  submittingCancel = false;

  openCancelModal(it: Interview): void {
    this.cancellingInterview = it;
    this.cancellationReasonInput = '';
    this.showCancelModal = true;
  }

  closeCancelModal(): void {
    this.showCancelModal = false;
    this.cancellingInterview = null;
    this.cancellationReasonInput = '';
  }

  confirmCancelInterview(): void {
    if (!this.cancellingInterview) return;

    this.submittingCancel = true;
    this.interviewService.cancelInterview(this.cancellingInterview.id, this.cancellationReasonInput).subscribe({
      next: (updated) => {
        this.submittingCancel = false;
        this.closeCancelModal();
        const index = this.interviews.findIndex(i => i.id === updated.id);
        if (index !== -1) {
          this.interviews[index] = updated;
        }
        this.successMessage = "L'entretien a bien été annulé. Un email d'annulation a été transmis au candidat.";
        setTimeout(() => this.successMessage = '', 5000);
      },
      error: (err) => {
        console.error(err);
        this.submittingCancel = false;
        const msg = err.error?.message || "Erreur lors de l'annulation de l'entretien.";
        this.errorMessage = msg;
        alert(msg);
      }
    });
  }

  updateStatus(newStatus: string): void {
    if (!this.candidate) return;
    this.candidateService.updateStatus(this.candidate.id, newStatus).subscribe({
      next: (data) => {
        this.candidate = data;
        this.successMessage = `Statut mis à jour avec succès : ${this.getStatusLabel(newStatus)}`;
        setTimeout(() => this.successMessage = '', 4000);
      },
      error: (err) => {
        console.error(err);
        this.errorMessage = "Erreur lors de la mise à jour du statut.";
      }
    });
  }

  downloadDocument(type: 'cv' | 'ml'): void {
    if (!this.candidate) return;
    const obs = type === 'cv' 
      ? this.candidateService.downloadCv(this.candidate.id)
      : this.candidateService.downloadMotivationLetter(this.candidate.id);

    obs.subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = type === 'cv' 
          ? (this.candidate?.cvFileName || `CV_${this.candidate?.lastName}.pdf`)
          : (this.candidate?.motivationLetterFileName || `Lettre_${this.candidate?.lastName}.pdf`);
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
      },
      error: (err) => {
        console.error(err);
        alert('Erreur lors du téléchargement du document.');
      }
    });
  }

  openInterviewConduct(id: number): void {
    this.router.navigate(['/manager/interviews', id]);
  }

  goBack(): void {
    this.router.navigate(['/manager/recruitment']);
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

  getInterviewTypeLabel(type: InterviewType): string {
    switch (type) {
      case 'PRESENTIEL': return 'Présentiel';
      case 'VISIO': return 'Visioconférence';
      case 'TELEPHONE': return 'Téléphonique';
      default: return type;
    }
  }
}

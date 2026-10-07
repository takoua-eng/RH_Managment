import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { CandidateService } from '../../../core/services/candidate.service';
import { DepartmentService } from '../../../core/services/department.service';
import { EmployeeService } from '../../../core/services/employee.service';
import { AuthService } from '../../../core/services/auth.service';
import { InterviewService } from '../../../core/services/interview.service';
import { AiQuestions, Candidate, Department, Employee, Interview, InterviewNotes } from '../../../core/models/interfaces';
import { AiAnalysisCardComponent } from '../../../core/components/ai-analysis-card/ai-analysis-card.component';

@Component({
  selector: 'app-candidate-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, AiAnalysisCardComponent],
  templateUrl: './candidate-detail.component.html',
  styleUrl: './candidate-detail.component.scss'
})
export class CandidateDetailComponent implements OnInit {
  candidate: Candidate | null = null;
  departments: Department[] = [];
  managers: Employee[] = [];
  managerName: string = 'Non assigné';
  interviews: Interview[] = [];
  selectedInterviewForNotes: Interview | null = null;
  
  loading = true;
  saving = false;
  successMessage = '';
  errorMessage = '';

  isEditing = false;
  canEdit = false;
  
  showTransmitModal = false;
  selectedManagerId: number | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private candidateService: CandidateService,
    private departmentService: DepartmentService,
    private employeeService: EmployeeService,
    private authService: AuthService,
    private interviewService: InterviewService
  ) {
    this.canEdit = this.authService.isAdmin() || this.authService.isRH();
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadDepartments(+id);
      this.loadManagers();
    } else {
      this.goBack();
    }
  }

  loadManagers(): void {
    this.employeeService.getManagers().subscribe({
      next: (data: any) => this.managers = data,
      error: (err: any) => console.error(err)
    });
  }

  loadDepartments(candidateId: number): void {
    this.departmentService.getAll().subscribe({
      next: (data: any) => {
        this.departments = data;
        this.loadCandidate(candidateId);
      },
      error: (err: any) => {
        console.error(err);
        this.loadCandidate(candidateId);
      }
    });
  }

  loadCandidate(id: number): void {
    this.candidateService.getCandidateById(id).subscribe({
      next: (data: any) => {
        this.candidate = data;
        this.resolveManagerName();
        this.loadInterviews(id);
        this.loading = false;
      },
      error: (err: any) => {
        console.error(err);
        this.errorMessage = "Impossible de charger les détails du candidat.";
        this.loading = false;
      }
    });
  }

  loadInterviews(candidateId: number): void {
    this.interviewService.getInterviewsByCandidate(candidateId).subscribe({
      next: (data) => this.interviews = data,
      error: (err) => console.error('Erreur chargement entretiens candidat:', err)
    });
  }

  parseAiQuestions(val: any): AiQuestions | null {
    if (!val) return null;
    if (typeof val === 'string') {
      try { return JSON.parse(val); } catch { return null; }
    }
    return val;
  }

  parseInterviewNotes(val: any): InterviewNotes | null {
    if (!val) return null;
    if (typeof val === 'string') {
      try { return JSON.parse(val); } catch { return null; }
    }
    return val;
  }

  getAskedQuestionsCount(it: Interview): number {
    const notes = this.parseInterviewNotes(it.interviewNotes);
    if (!notes || !notes.questions) return 0;
    return notes.questions.filter(q => q.posee).length;
  }

  getTotalQuestionsCount(it: Interview): number {
    const ai = this.parseAiQuestions(it.aiQuestions);
    if (!ai || !ai.questions) return 0;
    return ai.questions.length;
  }

  getLatestInterviewWithFeedback(): Interview | null {
    if (!this.interviews || this.interviews.length === 0) return null;
    return this.interviews.find(it => !!it.managerRecommendation) || null;
  }

  isConcordant(aiRec?: string | null, managerRec?: string | null): boolean {
    if (!aiRec || !managerRec) return false;
    return (aiRec === 'COMPATIBLE' && managerRec === 'FAVORABLE') ||
           (aiRec === 'A_EXAMINER' && managerRec === 'RESERVE') ||
           (aiRec === 'NON_COMPATIBLE' && managerRec === 'DEFAVORABLE');
  }

  openNotesModal(it: Interview): void {
    this.selectedInterviewForNotes = it;
  }

  closeNotesModal(): void {
    this.selectedInterviewForNotes = null;
  }

  getGroupedNotesQuestions(it: Interview): { category: string; questions: { text: string; posee: boolean; notes: string }[] }[] {
    const ai = this.parseAiQuestions(it.aiQuestions);
    const notes = this.parseInterviewNotes(it.interviewNotes);
    if (!ai || !ai.questions) return [];

    const notesMap: { [idx: number]: { posee: boolean; notes: string } } = {};
    if (notes && notes.questions) {
      notes.questions.forEach(q => { notesMap[q.index] = { posee: !!q.posee, notes: q.notes || '' }; });
    }

    const categoryOrder = [
      'Compétences confirmées',
      'Compétences à vérifier',
      'Expérience et projets',
      'Points d\'attention',
      'Motivation',
      'Savoir-être'
    ];

    const map = new Map<string, { text: string; posee: boolean; notes: string }[]>();

    ai.questions.forEach((q, idx) => {
      const cat = q.categorie?.trim() || 'Autres questions';
      if (!map.has(cat)) map.set(cat, []);
      const n = notesMap[idx] || { posee: false, notes: '' };
      map.get(cat)!.push({ text: q.question, posee: n.posee, notes: n.notes });
    });

    const result: { category: string; questions: { text: string; posee: boolean; notes: string }[] }[] = [];

    categoryOrder.forEach(cat => {
      if (map.has(cat)) {
        result.push({ category: cat, questions: map.get(cat)! });
        map.delete(cat);
      }
    });

    map.forEach((questions, category) => {
      result.push({ category, questions });
    });

    return result;
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

  getAiScorePercent(score?: number | null): number {
    if (score == null) return 0;
    return Math.round(score * 100);
  }

  getAiRecommendationLabel(rec?: string | null): string {
    switch (rec) {
      case 'COMPATIBLE': return 'Compatible';
      case 'A_EXAMINER': return 'À examiner';
      case 'NON_COMPATIBLE': return 'Non compatible';
      default: return rec || '';
    }
  }

  resolveManagerName(): void {
    if (!this.candidate) return;

    if (this.candidate.assignedManagerName) {
      this.managerName = this.candidate.assignedManagerName;
      return;
    }

    if (this.candidate.jobOfferDepartment) {
      const dep = this.departments.find(d => d.name === this.candidate!.jobOfferDepartment);
      if (dep && dep.managerName) {
        this.managerName = dep.managerName;
      } else {
        this.managerName = 'Non assigné';
      }
    }
  }

  toggleEdit(): void {
    if (this.canEdit) {
      this.isEditing = !this.isEditing;
      this.successMessage = '';
      this.errorMessage = '';
    }
  }

  saveCandidate(): void {
    if (!this.candidate || !this.canEdit) return;
    this.saving = true;
    this.successMessage = '';
    this.errorMessage = '';

    const updateData = {
      firstName: this.candidate.firstName,
      lastName: this.candidate.lastName,
      email: this.candidate.email,
      phone: this.candidate.phone,
      address: this.candidate.address,
      education: this.candidate.education,
      experience: this.candidate.experience,
      skills: this.candidate.skills
    };

    this.candidateService.updateCandidate(this.candidate.id, updateData).subscribe({
      next: (data: any) => {
        this.candidate = data;
        this.resolveManagerName();
        this.saving = false;
        this.isEditing = false;
        this.successMessage = "Les informations du candidat ont été mises à jour avec succès.";
      },
      error: (err: any) => {
        console.error(err);
        this.saving = false;
        this.errorMessage = "Une erreur est survenue lors de la mise à jour.";
      }
    });
  }

  updateStatus(newStatus: string): void {
    if (!this.candidate) return;
    
    this.candidateService.updateStatus(this.candidate.id, newStatus).subscribe({
      next: (data) => {
        this.candidate = data;
        this.successMessage = `Statut mis à jour : ${newStatus}`;
      },
      error: (err: any) => {
        console.error(err);
        this.errorMessage = "Erreur lors de la modification du statut.";
      }
    });
  }

  openTransmitModal(): void {
    this.selectedManagerId = null;
    this.showTransmitModal = true;
  }

  closeTransmitModal(): void {
    this.showTransmitModal = false;
    this.selectedManagerId = null;
  }

  confirmTransmit(): void {
    if (!this.candidate || !this.selectedManagerId) return;

    this.candidateService.transmitToManager(this.candidate.id, this.selectedManagerId).subscribe({
      next: (data) => {
        this.candidate = data;
        this.resolveManagerName();
        this.successMessage = "Candidature transmise au manager avec succès.";
        this.closeTransmitModal();
      },
      error: (err: any) => {
        console.error(err);
        this.errorMessage = "Erreur lors de la transmission.";
      }
    });
  }

  downloadDocument(type: 'cv' | 'ml'): void {
    if (!this.candidate) return;
    
    const request = type === 'cv' ? 
      this.candidateService.downloadCv(this.candidate.id) : 
      this.candidateService.downloadMotivationLetter(this.candidate.id);

    const fileName = type === 'cv' ? this.candidate.cvFileName : this.candidate.motivationLetterFileName;

    request.subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: (err: any) => {
        alert("Le fichier n'a pas pu être téléchargé.");
        console.error(err);
      }
    });
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

  goBack(): void {
    this.router.navigate(['/dashboard/admin-candidates']);
  }
}

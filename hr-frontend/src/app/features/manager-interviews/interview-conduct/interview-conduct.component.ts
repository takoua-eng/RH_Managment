import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { InterviewService } from '../../../core/services/interview.service';
import { 
  AiQuestion, 
  AiQuestions, 
  Interview, 
  InterviewNotes, 
  InterviewStatus, 
  ManagerAvisType, 
  NoteQuestion 
} from '../../../core/models/interfaces';

export interface QuestionWithIndex {
  globalIndex: number;
  question: AiQuestion;
}

export interface GroupedCategory {
  category: string;
  items: QuestionWithIndex[];
}

@Component({
  selector: 'app-interview-conduct',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './interview-conduct.component.html',
  styleUrl: './interview-conduct.component.scss'
})
export class InterviewConductComponent implements OnInit, OnDestroy {
  interviewId: number = 0;
  interview: Interview | null = null;
  aiQuestions: AiQuestions | null = null;
  
  loading = true;
  errorMessage = '';
  successMessage = '';

  // Notes state & Auto-save
  questionNotesMap: { [globalIndex: number]: NoteQuestion } = {};
  saveStatus: 'idle' | 'saving' | 'saved' | 'error' = 'idle';
  private autoSaveSubject = new Subject<void>();
  private autoSaveSub?: Subscription;

  // Cached View Model Data (Prevents Infinite Re-render / CD Loops)
  groupedQuestions: GroupedCategory[] = [];
  totalQuestionsCount = 0;
  askedQuestionsCount = 0;
  progressPercent = 0;

  // Polling state
  private pollingTimer: any = null;
  private pollCount = 0;
  isRegenerating = false;

  // Collapse state for "Ce qu'il faut écouter" (keyed by globalIndex)
  listenCollapseMap: { [globalIndex: number]: boolean } = {};

  // Feedback form state
  feedbackForm = {
    recommendation: '' as ManagerAvisType | '',
    rating: 0,
    comment: ''
  };
  submittingFeedback = false;
  isEditingFeedback = false;

  // Standard category ordering
  readonly CATEGORY_ORDER = [
    'Compétences confirmées',
    'Compétences à vérifier',
    'Expérience et projets',
    'Points d\'attention',
    'Motivation',
    'Savoir-être'
  ];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private interviewService: InterviewService
  ) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    const parsedId = idParam ? Number(idParam) : NaN;

    if (idParam && !isNaN(parsedId) && parsedId > 0) {
      this.interviewId = parsedId;
      this.loadInterview(this.interviewId);
    } else {
      this.loading = false;
      this.interview = null;
      this.errorMessage = "Entretien introuvable.";
    }

    // Debounce auto-save notes (1.5 seconds) - Triggered ONLY by user actions
    this.autoSaveSub = this.autoSaveSubject.pipe(
      debounceTime(1500)
    ).subscribe(() => {
      this.executeSaveNotes();
    });
  }

  ngOnDestroy(): void {
    if (this.autoSaveSub) {
      this.autoSaveSub.unsubscribe();
    }
    this.stopPolling();
  }

  loadInterview(id: number, silent = false): void {
    if (!silent) {
      this.loading = true;
      this.errorMessage = '';
    }

    const targetUrl = `http://localhost:8087/api/recruitment/interviews/${id}`;

    this.interviewService.getInterviewById(id).subscribe({
      next: (data) => {
        this.interview = data;
        this.aiQuestions = this.parseAiQuestions(data.aiQuestions);
        this.initNotesState(data);
        this.initFeedbackState(data);
        this.computeViewModel();
        this.loading = false;

        // Check if polling is needed (EN_ATTENTE) and not ANNULE
        if (data.aiQuestionsStatus === 'EN_ATTENTE' && data.status !== 'ANNULE' && this.pollCount < 5) {
          this.startPolling();
        } else {
          this.stopPolling();
          this.isRegenerating = false;
        }
      },
      error: (err) => {
        console.error(`Erreur lors de l'appel GET ${targetUrl} :`, err);
        const codeText = err.status ? `Erreur ${err.status} : ` : '';
        const backendMessage = err.error?.message || err.message || "Impossible de charger l'entretien.";
        this.errorMessage = `${codeText}${backendMessage}`;
        this.interview = null;
        this.loading = false;
        this.stopPolling();
        this.isRegenerating = false;
      }
    });
  }

  retryLoad(): void {
    if (this.interviewId > 0) {
      this.loadInterview(this.interviewId);
    }
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

  initNotesState(interview: Interview): void {
    this.questionNotesMap = {};
    const parsedNotes = this.parseInterviewNotes(interview.interviewNotes);

    if (parsedNotes && parsedNotes.questions && parsedNotes.questions.length > 0) {
      parsedNotes.questions.forEach(q => {
        this.questionNotesMap[q.index] = { index: q.index, posee: !!q.posee, notes: q.notes || '' };
      });
    } else {
      // Check localStorage fallback (read-only during init, no auto-save trigger)
      const localKey = 'interview_notes_' + interview.id;
      const localDataStr = localStorage.getItem(localKey);
      if (localDataStr) {
        try {
          const localObj = JSON.parse(localDataStr);
          if (localObj && localObj.questions) {
            localObj.questions.forEach((q: any) => {
              this.questionNotesMap[q.index] = { index: q.index, posee: !!q.posee, notes: q.notes || '' };
            });
            localStorage.removeItem(localKey);
          }
        } catch (e) {
          console.error('Erreur lecture localStorage notes:', e);
        }
      }
    }
  }

  initFeedbackState(interview: Interview): void {
    if (interview.managerRecommendation) {
      this.feedbackForm = {
        recommendation: interview.managerRecommendation as ManagerAvisType,
        rating: interview.managerRating || 0,
        comment: interview.managerFeedback || ''
      };
      this.isEditingFeedback = false;
    } else {
      this.feedbackForm = {
        recommendation: '',
        rating: 0,
        comment: ''
      };
      this.isEditingFeedback = true;
    }
  }

  computeViewModel(): void {
    if (!this.aiQuestions || !this.aiQuestions.questions) {
      this.groupedQuestions = [];
      this.totalQuestionsCount = 0;
      this.askedQuestionsCount = 0;
      this.progressPercent = 0;
      return;
    }

    const questions = this.aiQuestions.questions;
    this.totalQuestionsCount = questions.length;

    // Ensure all question indices have a map entry
    questions.forEach((_, idx) => {
      if (!this.questionNotesMap[idx]) {
        this.questionNotesMap[idx] = { index: idx, posee: false, notes: '' };
      }
    });

    // Group questions by category
    const map = new Map<string, QuestionWithIndex[]>();
    questions.forEach((q, idx) => {
      const cat = q.categorie?.trim() || 'Autres questions';
      if (!map.has(cat)) {
        map.set(cat, []);
      }
      map.get(cat)!.push({ globalIndex: idx, question: q });
    });

    const result: GroupedCategory[] = [];
    this.CATEGORY_ORDER.forEach(catName => {
      if (map.has(catName)) {
        result.push({ category: catName, items: map.get(catName)! });
        map.delete(catName);
      }
    });
    map.forEach((items, catName) => {
      result.push({ category: catName, items });
    });

    this.groupedQuestions = result;
    this.updateProgress();
  }

  updateProgress(): void {
    if (this.totalQuestionsCount === 0) {
      this.askedQuestionsCount = 0;
      this.progressPercent = 0;
      return;
    }
    this.askedQuestionsCount = Object.values(this.questionNotesMap).filter(n => n.posee).length;
    this.progressPercent = Math.round((this.askedQuestionsCount / this.totalQuestionsCount) * 100);
  }

  get isFeedbackSubmitted(): boolean {
    return !!(this.interview && this.interview.managerRecommendation);
  }

  onQuestionPoseeChange(globalIndex: number, posee: boolean): void {
    if (!this.questionNotesMap[globalIndex]) {
      this.questionNotesMap[globalIndex] = { index: globalIndex, posee: false, notes: '' };
    }
    this.questionNotesMap[globalIndex].posee = posee;
    this.updateProgress();
    this.triggerAutoSave();
  }

  onQuestionNotesChange(globalIndex: number, text: string): void {
    if (!this.questionNotesMap[globalIndex]) {
      this.questionNotesMap[globalIndex] = { index: globalIndex, posee: false, notes: '' };
    }
    this.questionNotesMap[globalIndex].notes = text;
    this.triggerAutoSave();
  }

  triggerAutoSave(): void {
    if (this.interview?.status === 'ANNULE') return;
    this.saveStatus = 'saving';
    this.autoSaveSubject.next();
  }

  executeSaveNotes(): void {
    if (!this.interviewId || this.interview?.status === 'ANNULE') return;

    const questionsList: NoteQuestion[] = Object.values(this.questionNotesMap);
    const payload: InterviewNotes = { questions: questionsList };

    this.interviewService.saveNotes(this.interviewId, payload).subscribe({
      next: (updated) => {
        this.saveStatus = 'saved';
        if (this.interview) {
          this.interview.interviewNotes = updated.interviewNotes;
        }
        setTimeout(() => {
          if (this.saveStatus === 'saved') this.saveStatus = 'idle';
        }, 2500);
      },
      error: (err) => {
        console.error('Erreur enregistrement des notes:', err);
        this.saveStatus = 'error';
      }
    });
  }

  toggleListenCollapse(globalIndex: number): void {
    this.listenCollapseMap[globalIndex] = !this.listenCollapseMap[globalIndex];
  }

  isListenCollapsed(globalIndex: number): boolean {
    return this.listenCollapseMap[globalIndex] !== false;
  }

  confirmRegenerate(): void {
    if (!confirm("Attention : régénérer les questions va réinitialiser les questions proposées. Souhaitez-vous continuer ?")) {
      return;
    }

    this.isRegenerating = true;
    this.interviewService.regenerateQuestions(this.interviewId).subscribe({
      next: () => {
        this.pollCount = 0;
        setTimeout(() => {
          this.loadInterview(this.interviewId, true);
        }, 3000);
      },
      error: (err) => {
        console.error('Erreur régénération des questions:', err);
        this.errorMessage = err.error?.message || "Erreur lors de la régénération des questions.";
        this.isRegenerating = false;
      }
    });
  }

  startPolling(): void {
    if (this.pollingTimer || this.interview?.status === 'ANNULE') return;
    this.pollCount = 0;
    this.pollingTimer = setInterval(() => {
      this.pollCount++;
      if (this.pollCount > 5) {
        this.stopPolling();
        return;
      }
      this.loadInterview(this.interviewId, true);
    }, 3000);
  }

  stopPolling(): void {
    if (this.pollingTimer) {
      clearInterval(this.pollingTimer);
      this.pollingTimer = null;
    }
  }

  openEditFeedback(): void {
    this.isEditingFeedback = true;
  }

  submitFeedback(): void {
    if (!this.feedbackForm.recommendation || !this.feedbackForm.rating) {
      this.errorMessage = "Veuillez sélectionner un avis et une note.";
      return;
    }

    this.submittingFeedback = true;
    this.errorMessage = '';
    this.successMessage = '';

    const payload = {
      recommendation: this.feedbackForm.recommendation,
      rating: this.feedbackForm.rating,
      comment: this.feedbackForm.comment
    };

    this.interviewService.submitFeedback(this.interviewId, payload).subscribe({
      next: (updated) => {
        this.interview = updated;
        this.submittingFeedback = false;
        this.isEditingFeedback = false;
        this.successMessage = "L'évaluation de l'entretien a été enregistrée avec succès. L'entretien est désormais marqué comme TERMINE.";
        setTimeout(() => this.successMessage = '', 5000);
      },
      error: (err) => {
        console.error('Erreur soumission feedback:', err);
        this.submittingFeedback = false;
        const msg = err.error?.message || "Une erreur est survenue lors de la soumission de l'évaluation.";
        this.errorMessage = msg;
      }
    });
  }

  // trackBy functions for *ngFor in template
  trackByCategory(index: number, item: GroupedCategory): string {
    return item.category;
  }

  trackByQuestion(index: number, item: QuestionWithIndex): number {
    return item.globalIndex;
  }

  trackByPoint(index: number, point: string): string {
    return point;
  }

  printPage(): void {
    window.print();
  }

  goToCandidate(): void {
    if (this.interview?.candidateId) {
      this.router.navigate(['/manager/recruitment', this.interview.candidateId]);
    }
  }

  goBack(): void {
    this.router.navigate(['/manager/interviews']);
  }

  getStatusBadgeClass(status?: InterviewStatus): string {
    switch (status) {
      case 'PLANIFIE': return 'bg-warning text-dark';
      case 'TERMINE': return 'bg-success text-white';
      case 'ANNULE': return 'bg-danger text-white';
      default: return 'bg-secondary text-white';
    }
  }

  getStatusLabel(status?: InterviewStatus): string {
    switch (status) {
      case 'PLANIFIE': return 'Planifié';
      case 'TERMINE': return 'Terminé';
      case 'ANNULE': return 'Annulé';
      default: return status || '';
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
}

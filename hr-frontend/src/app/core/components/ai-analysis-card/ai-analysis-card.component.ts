import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CandidateService } from '../../services/candidate.service';
import { Candidate } from '../../models/interfaces';

@Component({
  selector: 'app-ai-analysis-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ai-analysis-card.component.html',
  styleUrl: './ai-analysis-card.component.scss'
})
export class AiAnalysisCardComponent {
  @Input() candidate: Candidate | null = null;
  @Input() canRelaunch = false;
  @Output() relaunched = new EventEmitter<void>();

  isAnalyzing = false;
  isSkillsCollapsed = true;

  constructor(private candidateService: CandidateService) {}

  relaunchAnalysis(): void {
    if (!this.candidate) return;
    this.isAnalyzing = true;
    const candidateId = this.candidate.id;
    this.candidate.aiStatus = 'EN_ATTENTE';

    this.candidateService.relancerAnalyse(candidateId).subscribe({
      next: () => {
        setTimeout(() => {
          this.isAnalyzing = false;
          this.relaunched.emit();
        }, 3000);
      },
      error: (err: any) => {
        console.error(err);
        setTimeout(() => {
          this.isAnalyzing = false;
          this.relaunched.emit();
        }, 3000);
      }
    });
  }

  toggleSkillsCollapse(): void {
    this.isSkillsCollapsed = !this.isSkillsCollapsed;
  }

  getAiScorePercent(score?: number | null): number {
    if (score == null) return 0;
    return Math.round(score * 100);
  }

  getRecommendationBadgeClass(rec?: string | null): string {
    switch (rec) {
      case 'COMPATIBLE': return 'bg-success text-white';
      case 'A_EXAMINER': return 'bg-warning text-dark';
      case 'NON_COMPATIBLE': return 'bg-danger text-white';
      default: return 'bg-secondary text-white';
    }
  }

  getProgressBarClass(rec?: string | null): string {
    switch (rec) {
      case 'COMPATIBLE': return 'bg-success';
      case 'A_EXAMINER': return 'bg-warning';
      case 'NON_COMPATIBLE': return 'bg-danger';
      default: return 'bg-secondary';
    }
  }

  getRecommendationLabel(rec?: string | null): string {
    switch (rec) {
      case 'COMPATIBLE': return 'Compatible';
      case 'A_EXAMINER': return 'À examiner';
      case 'NON_COMPATIBLE': return 'Non compatible';
      default: return rec || 'Non évalué';
    }
  }

  getExperienceText(years?: number | null): string {
    if (years === undefined || years === null) return 'Expérience non précisée';
    return years > 1 ? `${years} ans d'expérience` : `${years} an d'expérience`;
  }

  getEducationText(level?: number | null): string {
    switch (level) {
      case 1: return 'Bac';
      case 2: return 'Bac+2';
      case 3: return 'Bac+3';
      case 4: return 'Bac+5';
      case 5: return 'Doctorat';
      case 0:
      default: return 'Niveau non précisé';
    }
  }

  getLanguagesText(languages?: string[] | null): string {
    if (!languages || languages.length === 0) return 'Langues non précisées';
    return languages.join(', ');
  }
}

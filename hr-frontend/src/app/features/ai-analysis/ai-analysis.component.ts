import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RecruitmentService } from '../../core/services/recruitment.service';

@Component({
  selector: 'app-ai-analysis',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="ai-analysis-page">
      <!-- Section Header -->
      <div class="row mb-4">
        <div class="col-12">
          <h2 class="fw-bold mb-1">Analyse IA de CV</h2>
          <p class="text-muted mb-0">Déposez un CV pour extraire automatiquement les compétences et évaluer la compatibilité avec vos offres.</p>
        </div>
      </div>

      <div class="row g-4">
        <!-- Left Side: Dropzone & Scanning -->
        <div class="col-lg-5">
          <div class="fluent-card dropzone-card d-flex flex-column align-items-center justify-content-center text-center p-5"
               [class.dragover]="isDragOver"
               (dragover)="onDragOver($event)"
               (dragleave)="onDragLeave($event)"
               (drop)="onDrop($event)"
               *ngIf="status === 'idle'">
            <div class="cloud-icon-box mb-4">
              <span class="material-symbols-outlined cloud-icon">cloud_upload</span>
            </div>
            <h4 class="fw-bold mb-2 fs-5">Glissez-déposez le CV ici</h4>
            <p class="text-muted fs-7 mb-4">Prend en charge les formats PDF, DOCX, PNG jusqu'à 10 Mo.</p>
            <span class="text-muted mb-4 fs-8">OU</span>
            <button (click)="fileInput.click()" class="fluent-btn-primary">
              <span class="material-symbols-outlined">search</span>
              Parcourir les fichiers
            </button>
            <input type="file" #fileInput (change)="onFileSelected($event)" style="display: none" accept=".pdf,.docx,.doc,.png,.jpg,.jpeg">
          </div>

          <!-- Scanning Animation Page -->
          <div class="fluent-card scanning-card text-center p-5" *ngIf="status === 'scanning'">
            <div class="scanner-container mb-4">
              <span class="material-symbols-outlined doc-icon">description</span>
              <div class="scanner-line"></div>
            </div>
            <h4 class="fw-bold mb-2 fs-5">Analyse IA en cours...</h4>
            <p class="text-primary fw-semibold fs-7 mb-3">{{ scanningMessage }}</p>
            <div class="progress" style="height: 6px;">
              <div class="progress-bar progress-bar-striped progress-bar-animated" [style.width.%]="scanningProgress"></div>
            </div>
            <span class="d-block text-muted fs-8 mt-2">{{ scanningProgress }}% complété</span>
          </div>

          <!-- Uploaded File Info (When Done) -->
          <div class="fluent-card p-4" *ngIf="status === 'done'">
            <div class="d-flex align-items-center justify-content-between mb-3 border-bottom pb-2">
              <span class="fw-bold fs-6">Document Analysé</span>
              <button (click)="resetAnalysis()" class="btn btn-sm btn-link text-decoration-none text-danger p-0 fs-7">Réanalyser</button>
            </div>
            <div class="d-flex align-items-center gap-3 p-2 bg-light-subtle rounded border">
              <span class="material-symbols-outlined text-danger fs-1">picture_as_pdf</span>
              <div class="overflow-hidden flex-grow-1">
                <h6 class="fw-bold mb-0 text-truncate">{{ analyzedFile?.cvName }}</h6>
                <span class="fs-8 text-muted d-block">Poste ciblé : {{ analyzedFile?.position }}</span>
              </div>
              <span class="fluent-badge success">Analysé</span>
            </div>
            <div class="mt-4 p-3 bg-light rounded text-start">
              <h6 class="fw-bold text-primary mb-2 d-flex align-items-center gap-1">
                <span class="material-symbols-outlined fs-5">info</span>
                Résumé IA de profil
              </h6>
              <p class="fs-7 text-dark mb-0">Candidature de **{{ analyzedFile?.name }}** pour le rôle de **{{ analyzedFile?.position }}**. Le profil présente une adéquation technique élevée, notamment sur les architectures conteneurisées et les méthodologies cloud natives.</p>
            </div>
          </div>
        </div>

        <!-- Right Side: Analysis Report (Stunning Results UI) -->
        <div class="col-lg-7">
          <div class="fluent-card h-100 d-flex flex-column justify-content-center text-center p-5 text-muted" *ngIf="status === 'idle'">
            <span class="material-symbols-outlined fs-1 mb-3">analytics</span>
            <h5 class="fw-bold mb-2">Rapport d'analyse IA</h5>
            <p class="mb-0 fs-7">Glissez-déposez le CV d'un candidat sur la gauche pour afficher le score de matching, les compétences détectées et les recommandations automatiques.</p>
          </div>

          <!-- Report Loading Placeholder -->
          <div class="fluent-card h-100 d-flex flex-column justify-content-center text-center p-5 text-muted" *ngIf="status === 'scanning'">
            <span class="spinner-border text-primary mb-3" role="status"></span>
            <h5>Génération du rapport d'adéquation...</h5>
          </div>

          <!-- Analysis Results Report -->
          <div class="fluent-card report-card text-start" *ngIf="status === 'done' && analyzedFile">
            <h4 class="fw-bold mb-4 border-bottom pb-2 d-flex align-items-center gap-2">
              <span class="material-symbols-outlined text-primary">analytics</span>
              Rapport d'Adéquation IA
            </h4>

            <div class="row g-4 align-items-center mb-4">
              <!-- Score Gauge -->
              <div class="col-md-5 text-center border-end">
                <div class="gauge-wrapper position-relative d-inline-block">
                  <svg class="gauge" viewBox="0 0 100 100">
                    <circle class="gauge-bg" cx="50" cy="50" r="45"></circle>
                    <circle class="gauge-value" cx="50" cy="50" r="45" [style.strokeDashoffset]="gaugeOffset"></circle>
                  </svg>
                  <div class="gauge-text">
                    <span class="number">{{ analyzedFile.matchPercent }}%</span>
                    <span class="label">Compatibilité</span>
                  </div>
                </div>
              </div>

              <!-- General Stats -->
              <div class="col-md-7">
                <h5 class="fw-bold mb-1">{{ analyzedFile.name }}</h5>
                <p class="text-secondary fs-7 mb-3">{{ analyzedFile.position }}</p>
                <div class="row g-2 text-center text-sm-start">
                  <div class="col-6">
                    <span class="d-block text-muted fs-8">Score Technique</span>
                    <span class="fw-bold text-success fs-6">95 / 100</span>
                  </div>
                  <div class="col-6">
                    <span class="d-block text-muted fs-8">Soft Skills</span>
                    <span class="fw-bold text-primary fs-6">88 / 100</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- Detected Skills Grid -->
            <div class="mb-4">
              <h5 class="fw-bold fs-7 text-uppercase text-secondary mb-2">Compétences Détectées</h5>
              <div class="d-flex flex-wrap gap-2">
                <span class="fluent-badge primary py-2 px-3 fs-7" *ngFor="let skill of analyzedFile.skills">
                  <span class="material-symbols-outlined fs-6 align-middle me-1">verified</span>
                  {{ skill }}
                </span>
              </div>
            </div>

            <!-- Strengths and Weaknesses -->
            <div class="row g-3">
              <div class="col-md-6">
                <div class="p-3 bg-success-light rounded-3 h-100">
                  <h6 class="fw-bold text-success mb-2 d-flex align-items-center gap-1 fs-7">
                    <span class="material-symbols-outlined fs-5">check_circle</span>
                    Points Forts
                  </h6>
                  <ul class="list-unstyled mb-0">
                    <li class="fs-7 py-1 d-flex align-items-center gap-2 text-dark" *ngFor="let str of analyzedFile.strengths">
                      <span class="material-symbols-outlined text-success fs-6">done</span>
                      {{ str }}
                    </li>
                  </ul>
                </div>
              </div>

              <div class="col-md-6">
                <div class="p-3 bg-warning-light rounded-3 h-100">
                  <h6 class="fw-bold text-warning mb-2 d-flex align-items-center gap-1 fs-7">
                    <span class="material-symbols-outlined fs-5">warning</span>
                    Compétences manquantes
                  </h6>
                  <ul class="list-unstyled mb-0">
                    <li class="fs-7 py-1 d-flex align-items-center gap-2 text-dark" *ngFor="let weak of analyzedFile.weaknesses">
                      <span class="material-symbols-outlined text-danger fs-6">close</span>
                      Manquant : {{ weak }}
                    </li>
                  </ul>
                </div>
              </div>
            </div>

            <!-- Action buttons -->
            <div class="mt-4 pt-3 border-top d-flex gap-2">
              <button (click)="scheduleInterviewSim()" class="fluent-btn-primary flex-grow-1 justify-content-center">
                <span class="material-symbols-outlined">calendar_today</span>
                Programmer un entretien
              </button>
              <button (click)="resetAnalysis()" class="fluent-btn-secondary">
                Retour
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .dropzone-card {
      border: 2px dashed var(--fluent-border) !important;
      background-color: var(--fluent-surface);
      height: 380px;
      transition: all 0.3s ease;
      
      &.dragover {
        border-color: var(--fluent-primary) !important;
        background-color: var(--fluent-primary-light) !important;
        transform: scale(1.02);
      }
    }
    .cloud-icon-box {
      width: 80px;
      height: 80px;
      border-radius: 50%;
      background-color: var(--fluent-primary-light);
      color: var(--fluent-primary);
      display: flex;
      align-items: center;
      justify-content: center;
      
      .cloud-icon {
        font-size: 3rem;
      }
    }
    .fs-7 {
      font-size: 0.85rem;
    }
    .fs-8 {
      font-size: 0.725rem;
    }
    .bg-success-light {
      background-color: var(--fluent-success-light);
    }
    .bg-warning-light {
      background-color: var(--fluent-warning-light);
    }
    
    /* Scanning Animation styling */
    .scanning-card {
      height: 380px;
      display: flex;
      flex-direction: column;
      justify-content: center;
    }
    .scanner-container {
      position: relative;
      width: 90px;
      height: 90px;
      margin: 0 auto;
      
      .doc-icon {
        font-size: 5rem;
        color: var(--fluent-text-secondary);
      }
    }
    .scanner-line {
      position: absolute;
      left: 0;
      width: 100%;
      height: 4px;
      background: linear-gradient(90deg, transparent, var(--fluent-primary), transparent);
      box-shadow: 0 0 8px var(--fluent-primary);
      animation: scanEffect 1.8s ease-in-out infinite;
    }
    
    /* SVG Gauge compatibility circle styling */
    .gauge-wrapper {
      width: 120px;
      height: 120px;
    }
    .gauge {
      width: 100%;
      height: 100%;
      transform: rotate(-90deg);
    }
    .gauge-bg {
      fill: none;
      stroke: var(--fluent-border);
      stroke-width: 8;
    }
    .gauge-value {
      fill: none;
      stroke: var(--fluent-success);
      stroke-width: 8;
      stroke-dasharray: 283; /* 2 * PI * r (r=45) = 282.7 */
      transition: stroke-dashoffset 1.5s ease;
    }
    .gauge-text {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      display: flex;
      flex-direction: column;
      line-height: 1.1;
      
      .number {
        font-size: 1.5rem;
        font-weight: 800;
        color: var(--fluent-text);
        font-family: 'Outfit', sans-serif;
      }
      .label {
        font-size: 0.65rem;
        color: var(--fluent-text-secondary);
        text-transform: uppercase;
        font-weight: 600;
      }
    }
    
    @keyframes scanEffect {
      0% { top: 0; }
      50% { top: 100%; }
      100% { top: 0; }
    }
  `]
})
export class AiAnalysisComponent implements OnInit {
  status: 'idle' | 'scanning' | 'done' = 'idle';
  isDragOver = false;

  scanningProgress = 0;
  scanningMessage = 'Lecture du fichier...';
  gaugeOffset = 283; // Completely empty initially

  analyzedFile: any = null;

  private progressInterval: any;
  private messageTimeout: any;

  constructor(private recruitmentService: RecruitmentService) {}

  ngOnInit(): void {
    // Check if there is a pending analysis triggered from recruitment table
    const pendingFile = localStorage.getItem('pending_ai_file');
    const pendingPosition = localStorage.getItem('pending_ai_position') || 'Développeur Fullstack';
    
    if (pendingFile) {
      localStorage.removeItem('pending_ai_file');
      localStorage.removeItem('pending_ai_position');
      
      this.runSimulation(pendingFile, pendingPosition);
    }
  }

  onDragOver(e: DragEvent) {
    e.preventDefault();
    this.isDragOver = true;
  }

  onDragLeave(e: DragEvent) {
    e.preventDefault();
    this.isDragOver = false;
  }

  onDrop(e: DragEvent) {
    e.preventDefault();
    this.isDragOver = false;
    const files = e.dataTransfer?.files;
    if (files && files.length > 0) {
      this.runSimulation(files[0].name);
    }
  }

  onFileSelected(e: Event) {
    const input = e.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.runSimulation(input.files[0].name);
    }
  }

  runSimulation(fileName: string, position: string = 'Développeur Fullstack') {
    this.status = 'scanning';
    this.scanningProgress = 0;
    this.scanningMessage = 'Lecture du document...';

    // Start progress count
    this.progressInterval = setInterval(() => {
      if (this.scanningProgress < 100) {
        this.scanningProgress += 5;
      }
    }, 100);

    // Text status messages transition
    this.messageTimeout = setTimeout(() => {
      this.scanningMessage = 'Extraction des données OCR...';
      
      this.messageTimeout = setTimeout(() => {
        this.scanningMessage = 'Analyse sémantique IA & compétences...';
        
        this.messageTimeout = setTimeout(() => {
          this.scanningMessage = 'Vérification de l\'adéquation poste...';
        }, 600);
      }, 600);
    }, 600);

    // Retrieve analysis result from service
    this.recruitmentService.simulateAiAnalysis(fileName, position).subscribe({
      next: (result) => {
        clearInterval(this.progressInterval);
        clearTimeout(this.messageTimeout);
        this.scanningProgress = 100;
        
        setTimeout(() => {
          this.analyzedFile = result;
          this.status = 'done';
          
          // Animate score gauge circle stroke-dashoffset:
          // Match compatibility score is 94.
          // Dashoffset = 283 - (283 * score / 100)
          setTimeout(() => {
            const score = result.matchPercent;
            this.gaugeOffset = 283 - (283 * score) / 100;
          }, 100);
        }, 400);
      }
    });
  }

  resetAnalysis() {
    this.status = 'idle';
    this.analyzedFile = null;
    this.scanningProgress = 0;
    this.gaugeOffset = 283;
  }

  scheduleInterviewSim() {
    if (this.analyzedFile) {
      alert(`Simulation : Entretien planifié pour ${this.analyzedFile.name}.`);
    }
  }
}

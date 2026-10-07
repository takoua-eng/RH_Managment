import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TrainingService } from '../../core/services/training.service';
import { AuthService } from '../../core/services/auth.service';
import { Training, TrainingEnrollment } from '../../core/models/interfaces';
import { CertificateComponent } from './certificate/certificate.component';

export interface ObjectivesParsedItem {
  text: string;
  isCheck: boolean;
}

export interface SyllabusSection {
  title: string;
  items: Array<{
    number?: string;
    text: string;
  }>;
}

@Component({
  selector: 'app-training',
  standalone: true,
  imports: [CommonModule, FormsModule, CertificateComponent],
  templateUrl: './training.component.html',
  styleUrl: './training.component.scss'
})
export class TrainingComponent implements OnInit, OnDestroy {
  catalogCourses: Training[] = [];
  myEnrollments: TrainingEnrollment[] = [];
  
  loading = true;
  successMessage = '';
  errorMessage = '';

  // Current logged in user name
  currentUserEmployeeName = 'Employé';

  // Syllabus Modal State
  showProgramModal = false;
  selectedTraining: Training | null = null;
  selectedEnrollmentForModal: TrainingEnrollment | null = null;
  parsedSyllabusSections: SyllabusSection[] = [];
  parsedObjectivesList: ObjectivesParsedItem[] = [];

  // Certificate Modal State
  showCertificateModal = false;
  selectedCertificateEnrollment: TrainingEnrollment | null = null;

  // Filters
  searchQuery = '';
  selectedCategory = '';
  selectedLevel = '';
  selectedAvailability = '';

  // My Enrollments Filters
  myEnrollmentsStatusFilter = '';

  constructor(
    private trainingService: TrainingService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.loadUserData();
    this.loadData();
  }

  ngOnDestroy(): void {
    document.body.classList.remove('modal-open');
  }

  private updateBodyScrollLock(): void {
    if (this.showProgramModal || this.showCertificateModal) {
      document.body.classList.add('modal-open');
    } else {
      document.body.classList.remove('modal-open');
    }
  }

  @HostListener('document:keydown.escape')
  onKeydownHandler() {
    if (this.showProgramModal) {
      this.closeProgramModal();
    }
    if (this.showCertificateModal) {
      this.closeCertificateModal();
    }
  }

  loadUserData() {
    this.authService.currentUser$.subscribe(user => {
      if (user) {
        this.currentUserEmployeeName = user.name || `${user.firstName || ''} ${user.lastName || ''}`.trim() || 'Employé';
      }
    });
  }

  getDynamicStatus(training?: Training): string {
    if (!training) return 'Disponible';
    if (training.status === 'Annulée') return 'Annulée';

    const today = new Date();
    today.setHours(0, 0, 0, 0);

    let start: Date | null = training.startDate ? new Date(training.startDate) : null;
    let end: Date | null = null;

    if (training.endDate) {
      end = new Date(training.endDate);
      end.setHours(23, 59, 59, 999);
    } else if (start) {
      let days = 1;
      if (training.duration) {
        const match = training.duration.match(/(\d+)/);
        if (match) days = parseInt(match[1], 10);
      }
      end = new Date(start.getTime() + (days - 1) * 24 * 60 * 60 * 1000);
      end.setHours(23, 59, 59, 999);
    }

    if (end && end < today) {
      return 'Terminée';
    }
    if (start && end && today >= start && today <= end) {
      return 'En cours';
    }
    return training.status || 'Disponible';
  }

  loadData() {
    this.loading = true;
    this.errorMessage = '';
    
    // Load catalog courses
    this.trainingService.getTrainingCatalog().subscribe({
      next: (courses) => {
        this.catalogCourses = courses.map(c => ({
          ...c,
          status: this.getDynamicStatus(c)
        }));
        
        // Load my enrollments
        this.trainingService.getMyEnrollments().subscribe({
          next: (enrollments) => {
            this.myEnrollments = enrollments.map(e => {
              const dynStatus = this.getDynamicStatus(e.training);
              let enrollStatus = e.status;
              if (dynStatus === 'Terminée' || e.progression === 100) {
                enrollStatus = 'Terminée';
              } else if (dynStatus === 'En cours' && enrollStatus !== 'Terminée') {
                enrollStatus = 'En cours';
              }
              return {
                ...e,
                status: enrollStatus as any,
                training: { ...e.training, status: dynStatus }
              };
            });
            this.loading = false;
          },
          error: (err) => {
            console.error('Failed to load employee enrollments:', err);
            this.errorMessage = 'Impossible de charger vos inscriptions.';
            this.loading = false;
          }
        });
      },
      error: (err) => {
        console.error('Failed to load training catalog:', err);
        this.errorMessage = 'Impossible de charger le catalogue des formations.';
        this.loading = false;
      }
    });
  }

  getAvailableCatalog(): Training[] {
    let courses = this.catalogCourses.filter(course => 
      !this.myEnrollments.some(enroll => enroll.training.id === course.id)
    );

    if (this.searchQuery) {
      const q = this.searchQuery.toLowerCase();
      courses = courses.filter(c => 
        c.title.toLowerCase().includes(q) || 
        (c.description && c.description.toLowerCase().includes(q)) ||
        (c.trainer && c.trainer.toLowerCase().includes(q))
      );
    }
    if (this.selectedCategory) {
      courses = courses.filter(c => c.category === this.selectedCategory);
    }
    if (this.selectedLevel) {
      courses = courses.filter(c => c.level === this.selectedLevel);
    }
    if (this.selectedAvailability === 'available') {
      courses = courses.filter(c => (c.availableSeats === undefined || c.availableSeats > 0));
    } else if (this.selectedAvailability === 'full') {
      courses = courses.filter(c => c.availableSeats === 0);
    }

    return courses;
  }

  getFilteredEnrollments(): TrainingEnrollment[] {
    if (!this.myEnrollmentsStatusFilter) {
      return this.myEnrollments;
    }
    return this.myEnrollments.filter(e => {
      const computedStatus = this.getCourseStatus(e.status, e.progression, e.training);
      return computedStatus === this.myEnrollmentsStatusFilter;
    });
  }

  enroll(course: Training) {
    this.errorMessage = '';
    this.successMessage = '';
    
    this.trainingService.enrollInTraining(course.id).subscribe({
      next: () => {
        this.successMessage = `Inscription réussie à la formation "${course.title}".`;
        this.loadData();
      },
      error: (err) => {
        console.error('Enrollment failed:', err);
        this.errorMessage = err.error?.message || "Une erreur s'est produite lors de l'inscription.";
      }
    });
  }

  study(enrollment: TrainingEnrollment) {
    this.errorMessage = '';
    this.successMessage = '';
    
    this.trainingService.updateProgress(enrollment.id).subscribe({
      next: () => {
        this.loadData();
      },
      error: (err) => {
        console.error('Study progress failed:', err);
        this.errorMessage = err.error?.message || "Erreur lors de la mise à jour de la progression.";
      }
    });
  }

  // Certificate Modal Actions
  openCertificateModal(enrollment: TrainingEnrollment) {
    this.selectedCertificateEnrollment = enrollment;
    this.showCertificateModal = true;
    this.updateBodyScrollLock();
  }

  closeCertificateModal() {
    this.showCertificateModal = false;
    this.selectedCertificateEnrollment = null;
    this.updateBodyScrollLock();
  }

  downloadCertificate(enrollment: TrainingEnrollment) {
    this.errorMessage = '';
    this.successMessage = '';

    this.trainingService.downloadCertificate(enrollment.id).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `attestation_${enrollment.training.title.replace(/\s+/g, '_')}.pdf`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
        this.successMessage = "Attestation officielle téléchargée avec succès !";
      },
      error: (err) => {
        console.error('Failed to download certificate:', err);
        this.errorMessage = "Erreur lors du téléchargement de l'attestation.";
      }
    });
  }

  // Syllabus Parsing Helper Methods
  parseObjectives(raw?: string): ObjectivesParsedItem[] {
    if (!raw) return [];
    const lines = raw.split('\n').map(l => l.trim()).filter(l => l.length > 0);
    return lines.map(line => {
      let clean = line;
      let isCheck = false;
      if (line.startsWith('-') || line.startsWith('•') || line.startsWith('*')) {
        clean = line.replace(/^[-•*]\s*/, '');
        isCheck = true;
      }
      return { text: clean, isCheck };
    });
  }

  parseSyllabus(raw?: string): SyllabusSection[] {
    if (!raw) return [];
    const lines = raw.split('\n').map(l => l.trim()).filter(l => l.length > 0);
    
    const sections: SyllabusSection[] = [];
    let currentSection: SyllabusSection = { title: 'Programme de la formation', items: [] };

    const sectionRegex = /^(Jour\s*\d+|Module\s*\d+|Partie\s*\d+|Semaine\s*\d+|Chapitre\s*\d+|Section\s*\d+|Phase\s*\d+)/i;
    const numberItemRegex = /^(\d+[\.\)\-]?)\s*(.*)/;

    for (const line of lines) {
      if (sectionRegex.test(line) || line.toLowerCase().startsWith('jour') || line.toLowerCase().startsWith('module') || line.toLowerCase().startsWith('partie') || line.toLowerCase().startsWith('semaine') || line.toLowerCase().startsWith('chapitre')) {
        if (currentSection.items.length > 0 || currentSection.title !== 'Programme de la formation') {
          sections.push(currentSection);
        }
        currentSection = { title: line, items: [] };
      } else {
        const match = line.match(numberItemRegex);
        if (match && match[1] && match[2]) {
          currentSection.items.push({
            number: match[1],
            text: match[2]
          });
        } else {
          const clean = line.replace(/^[-•*]\s*/, '');
          currentSection.items.push({
            text: clean
          });
        }
      }
    }

    if (currentSection.items.length > 0 || sections.length === 0) {
      sections.push(currentSection);
    }

    return sections;
  }

  viewProgram(course: Training, enrollment?: TrainingEnrollment) {
    this.selectedTraining = course;
    this.selectedEnrollmentForModal = enrollment || this.myEnrollments.find(e => e.training.id === course.id) || null;
    this.parsedObjectivesList = this.parseObjectives(course.objectives);
    this.parsedSyllabusSections = this.parseSyllabus(course.syllabus);
    this.showProgramModal = true;
    this.updateBodyScrollLock();
  }

  closeProgramModal() {
    this.showProgramModal = false;
    this.selectedTraining = null;
    this.selectedEnrollmentForModal = null;
    this.parsedSyllabusSections = [];
    this.parsedObjectivesList = [];
    this.updateBodyScrollLock();
  }

  getCourseStatus(status: string, progression: number, training?: Training): string {
    if (progression === 100 || status === 'Terminée') return 'Terminée';
    if (training) {
      const dyn = this.getDynamicStatus(training);
      if (dyn === 'Terminée') return 'Terminée';
      if (dyn === 'En cours') return 'En cours';
    }
    if (progression > 0 || status === 'En cours') return 'En cours';
    if (status === 'INSCRIT' || status === 'À venir') return status === 'INSCRIT' ? 'Inscrit' : 'À venir';
    return status || 'À venir';
  }

  getCourseBadgeClass(progression: number, status?: string): string {
    if (progression === 100) return 'success';
    if (progression === 0) {
      if (status === 'INSCRIT') return 'info';
      return 'secondary';
    }
    return 'primary';
  }

  getCourseIcon(title: string): string {
    const t = title.toLowerCase();
    if (t.includes('angular')) return 'code';
    if (t.includes('java') || t.includes('spring')) return 'coffee';
    if (t.includes('docker')) return 'layers';
    if (t.includes('kubernetes')) return 'sailing';
    return 'settings_suggest';
  }

  getCourseIconBg(title: string): string {
    const t = title.toLowerCase();
    if (t.includes('angular')) return 'red';
    if (t.includes('java') || t.includes('spring')) return 'yellow';
    if (t.includes('docker')) return 'blue';
    if (t.includes('kubernetes')) return 'cyan';
    return 'purple';
  }

  getAsciiProgressBar(progression: number): string {
    const totalBlocks = 12;
    const filledBlocks = Math.round((progression / 100) * totalBlocks);
    const emptyBlocks = totalBlocks - filledBlocks;
    return '█'.repeat(filledBlocks) + '░'.repeat(emptyBlocks);
  }

  formatDuration(duration?: string, location?: string): string {
    if (!duration && !location) return 'Non précisée';
    if (!location || location.trim() === '') return duration || '';
    if (!duration || duration.trim() === '') return location;
    return `${duration} (${location})`;
  }
}


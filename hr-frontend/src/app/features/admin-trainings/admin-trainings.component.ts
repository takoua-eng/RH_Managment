import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TrainingService } from '../../core/services/training.service';
import { Training } from '../../core/models/interfaces';
import { ConfirmDialogService } from '../../core/services/confirm-dialog.service';
import { ToastNotificationService } from '../../core/services/toast-notification.service';

@Component({
  selector: 'app-admin-trainings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-trainings.component.html',
  styleUrl: './admin-trainings.component.scss'
})
export class AdminTrainingsComponent implements OnInit {
  trainings: Training[] = [];
  loading = true;
  successMessage = '';
  errorMessage = '';

  showModal = false;
  isEditMode = false;
  selectedTraining: Partial<Training> = {};

  showDetailsModal = false;
  detailsTraining: Training | null = null;

  // Filters
  searchQuery = '';
  categoryFilter = '';
  levelFilter = '';
  statusFilter = '';

  constructor(
    private trainingService: TrainingService,
    private confirmService: ConfirmDialogService,
    private toastService: ToastNotificationService
  ) {}

  ngOnInit(): void {
    this.loadTrainings();
  }

  getDynamicStatus(training: Training): string {
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

  loadTrainings() {
    this.loading = true;
    this.trainingService.getTrainingCatalog().subscribe({
      next: (data) => {
        this.trainings = data.map(t => ({
          ...t,
          status: this.getDynamicStatus(t)
        }));
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement formations', err);
        this.errorMessage = 'Impossible de charger le catalogue des formations.';
        this.toastService.error('Impossible de charger le catalogue des formations.');
        this.loading = false;
      }
    });
  }

  get filteredTrainings(): Training[] {
    let result = this.trainings;

    if (this.searchQuery) {
      const q = this.searchQuery.toLowerCase();
      result = result.filter(t => 
        t.title.toLowerCase().includes(q) || 
        t.trainer?.toLowerCase().includes(q) ||
        t.description?.toLowerCase().includes(q)
      );
    }
    if (this.categoryFilter) {
      result = result.filter(t => t.category === this.categoryFilter);
    }
    if (this.levelFilter) {
      result = result.filter(t => t.level === this.levelFilter);
    }
    if (this.statusFilter) {
      result = result.filter(t => t.status === this.statusFilter);
    }

    return result;
  }

  submitted = false;

  openAddModal() {
    this.submitted = false;
    this.isEditMode = false;
    this.selectedTraining = {
      status: 'Disponible',
      level: 'Débutant',
      mode: 'En ligne',
      availableSeats: 10
    };
    this.showModal = true;
    this.clearMessages();
  }

  openEditModal(training: Training) {
    this.submitted = false;
    this.isEditMode = true;
    this.selectedTraining = { ...training };
    this.showModal = true;
    this.clearMessages();
  }

  closeModal() {
    this.showModal = false;
    this.submitted = false;
    this.selectedTraining = {};
  }

  openDetailsModal(training: Training) {
    this.detailsTraining = training;
    this.showDetailsModal = true;
  }

  closeDetailsModal() {
    this.showDetailsModal = false;
    this.detailsTraining = null;
  }

  calculateDuration() {
    if (this.selectedTraining.startDate && this.selectedTraining.endDate) {
      const start = new Date(this.selectedTraining.startDate);
      const end = new Date(this.selectedTraining.endDate);
      if (end < start) {
        this.selectedTraining.duration = '';
        return;
      }
      const diffTime = end.getTime() - start.getTime();
      const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24)) + 1; // +1 to include both start and end days
      
      if (diffDays > 0) {
        this.selectedTraining.duration = `${diffDays} jour${diffDays > 1 ? 's' : ''}`;
      }
    }
  }

  isDateRangeInvalid(): boolean {
    if (this.selectedTraining.startDate && this.selectedTraining.endDate) {
      const start = new Date(this.selectedTraining.startDate);
      const end = new Date(this.selectedTraining.endDate);
      return end < start;
    }
    return false;
  }

  isFormInvalid(): boolean {
    const t = this.selectedTraining;
    if (!t.title || !t.title.trim()) return true;
    if (!t.category || !t.category.trim()) return true;
    if (!t.level || !t.level.trim()) return true;
    if (!t.trainer || !t.trainer.trim()) return true;
    if (!t.startDate) return true;
    if (!t.duration || !t.duration.trim()) return true;
    if (t.availableSeats === undefined || t.availableSeats === null || t.availableSeats < 0) return true;
    if (this.isDateRangeInvalid()) return true;
    return false;
  }

  saveTraining() {
    this.submitted = true;
    if (this.isFormInvalid()) {
      if (this.isDateRangeInvalid()) {
        this.toastService.error('La date de fin ne peut pas être antérieure à la date de début.');
      } else {
        this.toastService.error('Veuillez remplir correctement tous les champs obligatoires.');
      }
      return;
    }

    this.clearMessages();
    if (this.isEditMode && this.selectedTraining.id) {
      this.trainingService.updateTraining(this.selectedTraining.id, this.selectedTraining).subscribe({
        next: () => {
          this.successMessage = 'Formation mise à jour avec succès.';
          this.toastService.success('Formation mise à jour avec succès.');
          this.closeModal();
          this.loadTrainings();
        },
        error: (err) => {
          console.error('Erreur mise à jour', err);
          this.errorMessage = 'Erreur lors de la mise à jour de la formation.';
          this.toastService.error('Erreur lors de la mise à jour de la formation.');
        }
      });
    } else {
      this.trainingService.createTraining(this.selectedTraining).subscribe({
        next: () => {
          this.successMessage = 'Nouvelle formation ajoutée avec succès.';
          this.toastService.success('Nouvelle formation ajoutée avec succès.');
          this.closeModal();
          this.loadTrainings();
        },
        error: (err) => {
          console.error('Erreur ajout', err);
          this.errorMessage = 'Erreur lors de l\'ajout de la formation.';
          this.toastService.error('Erreur lors de l\'ajout de la formation.');
        }
      });
    }
  }

  async deleteTraining(id: number): Promise<void> {
    const confirmed = await this.confirmService.showConfirm({
      title: 'Suppression de formation',
      message: 'Êtes-vous sûr de vouloir supprimer cette formation ? Cette action est irréversible.',
      confirmText: 'Supprimer',
      cancelText: 'Annuler',
      type: 'danger'
    });

    if (confirmed) {
      this.clearMessages();
      this.trainingService.deleteTraining(id).subscribe({
        next: () => {
          this.successMessage = 'Formation supprimée.';
          this.toastService.success('Formation supprimée avec succès.');
          this.loadTrainings();
        },
        error: (err) => {
          console.error('Erreur suppression', err);
          this.errorMessage = 'Erreur lors de la suppression de la formation.';
          this.toastService.error('Erreur lors de la suppression de la formation.');
        }
      });
    }
  }

  clearMessages() {
    this.successMessage = '';
    this.errorMessage = '';
  }
}

import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { JobOfferService } from '../../core/services/job-offer.service';
import { JobOffer } from '../../core/models/interfaces';
import { ConfirmDialogService } from '../../core/services/confirm-dialog.service';
import { ToastNotificationService } from '../../core/services/toast-notification.service';

@Component({
  selector: 'app-admin-job-offers',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-job-offers.component.html',
  styleUrl: './admin-job-offers.component.scss'
})
export class AdminJobOffersComponent implements OnInit {
  jobOffers: JobOffer[] = [];
  filteredOffers: JobOffer[] = [];
  
  loading = false;
  successMessage = '';
  errorMessage = '';

  // Filtres
  searchQuery = '';
  departmentFilter = '';
  statusFilter = '';
  contractFilter = '';
  experienceFilter = '';

  // Modal State
  showModal = false;
  isEditing = false;
  currentOffer: JobOffer = this.getEmptyOffer();
  
  // Custom form fields for salary
  minSalary: number | null = null;
  maxSalary: number | null = null;

  constructor(
    private jobOfferService: JobOfferService,
    private confirmService: ConfirmDialogService,
    private toastService: ToastNotificationService
  ) {}

  ngOnInit(): void {
    this.loadOffers();
  }

  getEmptyOffer(): JobOffer {
    return {
      title: '',
      description: '',
      department: '',
      contractType: '',
      experienceLevel: '',
      requiredSkills: '',
      location: '',
      salaryRange: '',
      applicationDeadline: new Date().toISOString().split('T')[0],
      numberOfPositions: 1,
      status: 'DRAFT'
    };
  }

  loadOffers() {
    this.loading = true;
    this.jobOfferService.getAllOffers().subscribe({
      next: (data) => {
        this.jobOffers = data;
        this.applyFilters();
        this.loading = false;
      },
      error: (err) => {
        this.errorMessage = "Erreur lors du chargement des offres.";
        this.toastService.error("Erreur lors du chargement des offres.");
        this.loading = false;
        console.error(err);
      }
    });
  }

  applyFilters() {
    this.filteredOffers = this.jobOffers.filter(offer => {
      const matchQuery = this.searchQuery ? 
        (offer.title.toLowerCase().includes(this.searchQuery.toLowerCase()) || 
         offer.department.toLowerCase().includes(this.searchQuery.toLowerCase())) : true;
      const matchDept = this.departmentFilter ? offer.department === this.departmentFilter : true;
      const matchStatus = this.statusFilter ? offer.status === this.statusFilter : true;
      const matchContract = this.contractFilter ? offer.contractType === this.contractFilter : true;
      const matchExperience = this.experienceFilter ? offer.experienceLevel === this.experienceFilter : true;
      
      return matchQuery && matchDept && matchStatus && matchContract && matchExperience;
    });
  }

  clearMessages() {
    this.successMessage = '';
    this.errorMessage = '';
  }

  openAddModal() {
    this.isEditing = false;
    this.currentOffer = this.getEmptyOffer();
    this.minSalary = null;
    this.maxSalary = null;
    this.showModal = true;
    this.clearMessages();
  }

  openEditModal(offer: JobOffer) {
    this.isEditing = true;
    this.currentOffer = { ...offer };
    
    // Parse salary range if it exists
    this.minSalary = null;
    this.maxSalary = null;
    if (offer.salaryRange && offer.salaryRange.includes('-')) {
      const parts = offer.salaryRange.split('-');
      const min = parseInt(parts[0].replace(/\D/g, ''));
      const max = parseInt(parts[1].replace(/\D/g, ''));
      if (!isNaN(min)) this.minSalary = min;
      if (!isNaN(max)) this.maxSalary = max;
    } else if (offer.salaryRange) {
      const val = parseInt(offer.salaryRange.replace(/\D/g, ''));
      if (!isNaN(val)) this.minSalary = val;
    }

    this.showModal = true;
    this.clearMessages();
  }

  closeModal() {
    this.showModal = false;
  }

  saveOffer() {
    // Format salary range
    if (this.minSalary && this.maxSalary) {
      this.currentOffer.salaryRange = `${this.minSalary} DT - ${this.maxSalary} DT`;
    } else if (this.minSalary) {
      this.currentOffer.salaryRange = `${this.minSalary} DT`;
    } else {
      this.currentOffer.salaryRange = '';
    }

    if (this.isEditing && this.currentOffer.id) {
      this.jobOfferService.updateOffer(this.currentOffer.id, this.currentOffer).subscribe({
        next: () => {
          this.successMessage = 'Offre modifiée avec succès.';
          this.toastService.success('Offre modifiée avec succès.');
          this.closeModal();
          this.loadOffers();
        },
        error: () => {
          this.errorMessage = "Erreur lors de la modification de l'offre.";
          this.toastService.error("Erreur lors de la modification de l'offre.");
        }
      });
    } else {
      this.jobOfferService.createOffer(this.currentOffer).subscribe({
        next: () => {
          this.successMessage = 'Offre créée avec succès.';
          this.toastService.success('Offre créée avec succès.');
          this.closeModal();
          this.loadOffers();
        },
        error: () => {
          this.errorMessage = "Erreur lors de la création de l'offre.";
          this.toastService.error("Erreur lors de la création de l'offre.");
        }
      });
    }
  }

  updateStatus(offer: JobOffer, newStatus: 'DRAFT' | 'PUBLISHED' | 'CLOSED') {
    if (!offer.id) return;
    const updatedOffer = { ...offer, status: newStatus };
    this.jobOfferService.updateOffer(offer.id, updatedOffer).subscribe({
      next: () => {
        this.successMessage = `Statut mis à jour : ${newStatus}`;
        this.toastService.success(`Statut mis à jour : ${newStatus}`);
        this.loadOffers();
      },
      error: () => {
        this.errorMessage = "Erreur lors de la mise à jour du statut.";
        this.toastService.error("Erreur lors de la mise à jour du statut.");
      }
    });
  }

  async deleteOffer(id: number | undefined): Promise<void> {
    if (!id) return;

    const confirmed = await this.confirmService.showConfirm({
      title: 'Suppression d\'offre d\'emploi',
      message: 'Voulez-vous vraiment supprimer cette offre d\'emploi ? Cette action est irréversible.',
      confirmText: 'Supprimer',
      cancelText: 'Annuler',
      type: 'danger'
    });

    if (confirmed) {
      this.jobOfferService.deleteOffer(id).subscribe({
        next: () => {
          this.successMessage = 'Offre supprimée.';
          this.toastService.success('Offre d\'emploi supprimée avec succès.');
          this.loadOffers();
        },
        error: () => {
          this.errorMessage = "Erreur lors de la suppression.";
          this.toastService.error("Erreur lors de la suppression de l'offre.");
        }
      });
    }
  }

  getStatusBadgeClass(status: string): string {
    switch(status) {
      case 'PUBLISHED': return 'bg-success-subtle text-success';
      case 'DRAFT': return 'bg-secondary-subtle text-secondary';
      case 'CLOSED': return 'bg-danger-subtle text-danger';
      default: return 'bg-light text-dark';
    }
  }
}

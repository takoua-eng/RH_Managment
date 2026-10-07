import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { CandidateService } from '../../core/services/candidate.service';
import { JobOffer } from '../../core/models/interfaces';

@Component({
  selector: 'app-public-apply',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './public-apply.component.html',
  styleUrl: './public-apply.component.scss'
})
export class PublicApplyComponent implements OnInit {
  offer: JobOffer | null = null;
  loading = true;
  submitting = false;
  errorMessage = '';
  successMessage = '';

  // Form Data
  candidateData = {
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    address: '',
    education: '',
    experience: '',
    skills: '',
    jobOfferId: null as number | null
  };

  cvFile: File | null = null;
  motivationLetterFile: File | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private candidateService: CandidateService
  ) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      const offerId = parseInt(idParam, 10);
      this.candidateData.jobOfferId = offerId;
      this.loadOffer(offerId);
    } else {
      this.errorMessage = "Offre non spécifiée.";
      this.loading = false;
    }
  }

  loadOffer(id: number) {
    this.candidateService.getPublicOffer(id).subscribe({
      next: (data) => {
        this.offer = data;
        this.loading = false;
      },
      error: (err) => {
        this.errorMessage = "Cette offre n'existe pas ou n'est plus disponible.";
        this.loading = false;
        console.error(err);
      }
    });
  }

  onFileChange(event: any, type: 'cv' | 'ml') {
    const file = event.target.files[0];
    if (file) {
      const allowedTypes = ['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'application/msword'];
      const allowedExts = ['.pdf', '.docx', '.doc'];
      const ext = file.name.substring(file.name.lastIndexOf('.')).toLowerCase();

      if (!allowedTypes.includes(file.type) && !allowedExts.includes(ext)) {
        this.errorMessage = "Seuls les formats PDF et Word (.docx) sont autorisés.";
        event.target.value = ''; // reset
        return;
      }
      if (file.size > 10 * 1024 * 1024) { // 10MB limit
        this.errorMessage = "La taille du fichier ne doit pas dépasser 10 Mo.";
        event.target.value = ''; // reset
        return;
      }

      this.errorMessage = '';
      if (type === 'cv') {
        this.cvFile = file;
      } else {
        this.motivationLetterFile = file;
      }
    }
  }

  submitApplication() {
    if (!this.cvFile || !this.motivationLetterFile) {
      alert("Veuillez joindre votre CV et votre lettre de motivation.");
      return;
    }

    this.submitting = true;
    this.errorMessage = '';
    
    const jsonString = JSON.stringify(this.candidateData);
    
    this.candidateService.apply(jsonString, this.cvFile, this.motivationLetterFile).subscribe({
      next: (res) => {
        this.successMessage = "Votre candidature a été envoyée avec succès ! Vous pouvez maintenant fermer cette page.";
        this.submitting = false;
        this.resetForm();
      },
      error: (err) => {
        this.submitting = false;
        if (err.status === 409) {
          this.errorMessage = "Vous avez déjà postulé à cette offre avec cette adresse email.";
        } else if (err.error && err.error.message) {
          this.errorMessage = err.error.message;
        } else {
          this.errorMessage = "Une erreur est survenue lors de l'envoi de votre candidature.";
        }
      }
    });
  }

  resetForm() {
    this.candidateData = {
      firstName: '',
      lastName: '',
      email: '',
      phone: '',
      address: '',
      education: '',
      experience: '',
      skills: '',
      jobOfferId: this.candidateData.jobOfferId
    };
    this.cvFile = null;
    this.motivationLetterFile = null;
    // We cannot easily reset file inputs bindings without viewChild, but hiding the form is enough
  }

  closePage() {
    // Navigate to a generic home or just stay here, since it's a public page
    window.location.href = 'https://www.google.com'; // Or an enterprise public site
  }
}

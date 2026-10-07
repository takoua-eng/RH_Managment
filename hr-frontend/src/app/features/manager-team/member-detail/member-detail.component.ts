import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';

import { AuthService } from '../../../core/services/auth.service';
import { ManagerTeamService, TeamMember } from '../../../core/services/manager-team.service';
import { TrainingService } from '../../../core/services/training.service';
import { TrainingEnrollment } from '../../../core/models/interfaces';

@Component({
  selector: 'app-member-detail',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './member-detail.component.html',
  styleUrl: './member-detail.component.scss'
})
export class MemberDetailComponent implements OnInit, OnDestroy {

  member: TeamMember | null = null;
  loading = true;
  error = false;
  errorMessage = '';

  enrollments: TrainingEnrollment[] = [];
  loadingEnrollments = true;

  private sub = new Subscription();
  private currentEmpId: number | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private authService: AuthService,
    private teamService: ManagerTeamService,
    private trainingService: TrainingService
  ) {}

  ngOnInit(): void {
    const empId = Number(this.route.snapshot.paramMap.get('id'));
    if (empId) {
      this.currentEmpId = empId;
      this.loadMember(empId);
      this.loadEnrollments(empId);
    } else {
      this.loading = false;
      this.error = true;
      this.errorMessage = 'ID d\'employé invalide.';
    }
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  loadMember(employeeId: number): void {
    this.loading = true;
    this.error = false;

    this.sub.add(
      this.teamService.getTeamMemberDetail(employeeId).subscribe({
        next: (data) => {
          this.member  = data;
          this.loading = false;
          this.error   = false;
        },
        error: (err) => {
          console.error('Erreur chargement membre :', err);
          this.member = null;
          this.loading = false;
          this.error = true;
          this.errorMessage = 'Impossible de charger les détails de cet employé depuis la base de données.';
        }
      })
    );
  }

  retry(): void {
    if (this.currentEmpId) {
      this.loadMember(this.currentEmpId);
      this.loadEnrollments(this.currentEmpId);
    }
  }

  private loadEnrollments(employeeId: number): void {
    this.loadingEnrollments = true;
    this.sub.add(
      this.trainingService.getEmployeeEnrollments(employeeId).subscribe({
        next: (data) => {
          this.enrollments = data || [];
          this.loadingEnrollments = false;
        },
        error: (err) => {
          console.error('Erreur chargement formations :', err);
          this.enrollments = [];
          this.loadingEnrollments = false;
        }
      })
    );
  }

  goBack(): void {
    this.router.navigate(['/manager/team']);
  }

  getInitials(member: TeamMember | null): string {
    if (!member) return 'EMP';
    return ((member.firstName?.[0] ?? '') + (member.lastName?.[0] ?? '')).toUpperCase() || 'EMP';
  }

  getPhotoUrl(id: number): string {
    return this.teamService.getPhotoUrl(id);
  }

  onImgError(event: Event): void {
    (event.target as HTMLImageElement).style.display = 'none';
  }

  getStatusClass(status?: string): string {
    switch (status) {
      case 'ACTIVE':   return 'status-active';
      case 'INACTIVE': return 'status-inactive';
      case 'ON_LEAVE': return 'status-leave';
      default:         return 'status-active';
    }
  }

  getStatusLabel(status?: string): string {
    switch (status) {
      case 'ACTIVE':   return 'Actif';
      case 'INACTIVE': return 'Inactif';
      case 'ON_LEAVE': return 'En congé';
      default:         return 'Actif';
    }
  }

  formatDate(dateStr?: string): string {
    if (!dateStr) return '—';
    try {
      return new Date(dateStr).toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });
    } catch { return dateStr; }
  }

  formatFullDate(dateStr?: string): string {
    if (!dateStr) return '—';
    try {
      return new Date(dateStr).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
    } catch { return dateStr; }
  }

  downloadCertificate(enrollment: TrainingEnrollment): void {
    this.trainingService.downloadCertificate(enrollment.id).subscribe((blob) => {
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `Certificat_${enrollment.training.title}.pdf`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    });
  }
}

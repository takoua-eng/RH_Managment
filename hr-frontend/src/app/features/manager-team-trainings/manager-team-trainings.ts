import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { TrainingService } from '../../core/services/training.service';
import { AuthService, UserSession } from '../../core/services/auth.service';
import { TrainingEnrollment } from '../../core/models/interfaces';

@Component({
  selector: 'app-manager-team-trainings',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './manager-team-trainings.html',
  styleUrl: './manager-team-trainings.scss'
})
export class ManagerTeamTrainingsComponent implements OnInit, OnDestroy {
  enrollments: TrainingEnrollment[] = [];
  loading = true;
  private sub = new Subscription();

  constructor(
    private trainingService: TrainingService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.sub.add(
      this.authService.currentUser$.subscribe(session => {
        if (session && session.id) {
          this.loadTeamTrainings(session.id);
        } else {
          this.loading = false;
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  getDynamicStatus(training: any): string {
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

  private loadTeamTrainings(managerId: number): void {
    this.sub.add(
      this.trainingService.getTeamTrainings(managerId).subscribe({
        next: (data) => {
          this.enrollments = data.map(e => {
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
          console.error('Erreur chargement formations équipe', err);
          this.loading = false;
        }
      })
    );
  }

  getProgressColor(progress: number): string {
    if (progress === 100) return 'bg-success';
    if (progress > 50) return 'bg-primary';
    if (progress > 0) return 'bg-warning';
    return 'bg-secondary';
  }
}

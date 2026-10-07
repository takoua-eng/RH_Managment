import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { AuthService, UserSession } from '../../../core/services/auth.service';
import { LeaveService } from '../../../core/services/leave.service';
import { ConfirmDialogService } from '../../../core/services/confirm-dialog.service';
import { ToastNotificationService } from '../../../core/services/toast-notification.service';

@Component({
  selector: 'app-employee-leave',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './employee-leave.component.html',
  styleUrl: './employee-leave.component.scss'
})
export class EmployeeLeaveComponent implements OnInit, OnDestroy {
  currentUser: UserSession | null = null;
  leaveHistory: any[] = [];
  leaveForm: FormGroup;
  private sub = new Subscription();

  loading = true;
  submitting = false;
  successMessage = '';
  errorMessage = '';
  calculatedDays = 0;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private leaveService: LeaveService,
    private confirmService: ConfirmDialogService,
    private toastService: ToastNotificationService
  ) {
    const today = new Date().toISOString().split('T')[0];
    this.leaveForm = this.fb.group({
      type: ['ANNUAL', Validators.required],
      startDate: [today, Validators.required],
      endDate: [today, Validators.required],
      reason: ['', Validators.maxLength(255)]
    }, { validators: this.dateLessThan('startDate', 'endDate') });
  }

  ngOnInit(): void {
    // Listen to form value changes to calculate days dynamically
    this.sub.add(
      this.leaveForm.valueChanges.subscribe(() => {
        this.updateCalculatedDays();
      })
    );

    this.sub.add(
      this.authService.currentUser$.subscribe({
        next: (user) => {
          this.currentUser = user;
          if (user?.id) {
            this.loadLeaveHistory(user.id);
          }
        },
        error: (err) => {
          console.error('Failed to load user session:', err);
          this.errorMessage = 'Erreur lors du chargement de la session.';
          this.toastService.error('Erreur lors du chargement de la session.');
          this.loading = false;
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  loadLeaveHistory(employeeId: number): void {
    this.loading = true;
    this.leaveService.getLeaveHistory(employeeId).subscribe({
      next: (history) => {
        this.leaveHistory = history || [];
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load leave history:', err);
        this.errorMessage = 'Impossible de charger l\'historique des congés.';
        this.toastService.error('Impossible de charger l\'historique des congés.');
        this.loading = false;
      }
    });
  }

  dateLessThan(from: string, to: string) {
    return (group: FormGroup): {[key: string]: any} | null => {
      const f = group.controls[from];
      const t = group.controls[to];
      if (f.value && t.value && new Date(f.value) > new Date(t.value)) {
        return {
          datesInverted: true
        };
      }
      return null;
    };
  }

  updateCalculatedDays(): void {
    const startVal = this.leaveForm.get('startDate')?.value;
    const endVal = this.leaveForm.get('endDate')?.value;

    if (!startVal || !endVal) {
      this.calculatedDays = 0;
      return;
    }

    const start = new Date(startVal);
    const end = new Date(endVal);

    if (start > end) {
      this.calculatedDays = 0;
      return;
    }

    const diffTime = Math.abs(end.getTime() - start.getTime());
    this.calculatedDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24)) + 1;
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'PENDING': return 'En attente';
      case 'APPROVED_RH':
      case 'APPROVED_MANAGER': return 'Approuvé';
      case 'REJECTED': return 'Refusé';
      default: return status;
    }
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'PENDING': return 'badge-pending';
      case 'APPROVED_RH':
      case 'APPROVED_MANAGER': return 'badge-approved';
      case 'REJECTED': return 'badge-rejected';
      default: return 'badge-secondary';
    }
  }

  getTypeLabel(type: string): string {
    switch (type) {
      case 'ANNUAL': return 'Annuel';
      case 'SICK': return 'Maladie';
      case 'OTHER': return 'RTT / Sans Solde';
      default: return type;
    }
  }

  onSubmit(): void {
    if (this.leaveForm.invalid || !this.currentUser?.id) {
      this.leaveForm.markAllAsTouched();
      this.toastService.error('Veuillez remplir correctement les champs du formulaire.');
      return;
    }

    this.submitting = true;
    this.successMessage = '';
    this.errorMessage = '';

    const val = this.leaveForm.value;
    const payload = {
      employeeId: this.currentUser.id,
      startDate: val.startDate,
      endDate: val.endDate,
      type: val.type,
      reason: val.reason
    };

    this.leaveService.requestLeave(payload).subscribe({
      next: () => {
        this.submitting = false;
        this.successMessage = 'Votre demande de congé a été enregistrée avec succès !';
        this.toastService.success('Demande de congé envoyée avec succès !');
        this.leaveForm.reset({
          type: 'ANNUAL',
          startDate: new Date().toISOString().split('T')[0],
          endDate: new Date().toISOString().split('T')[0],
          reason: ''
        });
        if (this.currentUser?.id) {
          this.loadLeaveHistory(this.currentUser.id);
        }
      },
      error: (err) => {
        console.error('Leave request failed:', err);
        this.submitting = false;
        const msg = err.error?.message || 'Erreur lors de la soumission de la demande.';
        this.errorMessage = msg;
        this.toastService.error(msg);
      }
    });
  }

  async cancelLeave(leaveId: number): Promise<void> {
    const confirmed = await this.confirmService.showConfirm({
      title: 'Annulation de congé',
      message: 'Voulez-vous vraiment annuler cette demande de congé ?',
      confirmText: 'Oui, annuler',
      cancelText: 'Non, garder',
      type: 'warning'
    });

    if (!confirmed) {
      return;
    }

    this.successMessage = '';
    this.errorMessage = '';

    this.leaveService.cancelLeave(leaveId).subscribe({
      next: () => {
        this.successMessage = 'Demande de congé annulée avec succès !';
        this.toastService.success('Demande de congé annulée avec succès !');
        if (this.currentUser?.id) {
          this.loadLeaveHistory(this.currentUser.id);
        }
      },
      error: (err) => {
        console.error('Cancel leave failed:', err);
        this.errorMessage = 'Erreur lors de l\'annulation de la demande.';
        this.toastService.error('Erreur lors de l\'annulation de la demande.');
      }
    });
  }
}

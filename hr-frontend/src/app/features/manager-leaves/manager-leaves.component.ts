import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';

import { AuthService } from '../../core/services/auth.service';
import {
  ManagerLeaveService,
  ManagerLeave,
  LeaveStatus
} from '../../core/services/manager-leave.service';

interface FilterTab {
  label:  string;
  value:  LeaveStatus | 'ALL';
  icon:   string;
  count:  number | null;
}

interface Toast {
  show:    boolean;
  message: string;
  type:    'success' | 'error';
  icon:    string;
}

@Component({
  selector: 'app-manager-leaves',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './manager-leaves.component.html',
  styleUrl: './manager-leaves.component.scss'
})
export class ManagerLeavesComponent implements OnInit, OnDestroy {

  // =========================================================
  // ÉTAT
  // =========================================================

  all:      ManagerLeave[] = [];
  filtered: ManagerLeave[] = [];
  loading = true;

  activeTab: LeaveStatus | 'ALL' = 'ALL';

  managerId: number | null = null;
  actionInProgress = false;

  // Modal refus
  showRejectModal = false;
  selectedLeave: ManagerLeave | null = null;
  rejectReason = '';

  // Toast
  toast: Toast = { show: false, message: '', type: 'success', icon: 'check_circle' };

  private sub = new Subscription();


  // =========================================================
  // TABS
  // =========================================================

  tabs: FilterTab[] = [
    { label: 'Tout',              value: 'ALL',               icon: 'list_alt',      count: null },
    { label: 'À valider',         value: 'PENDING',           icon: 'schedule',      count: null },
    { label: 'En attente RH',     value: 'APPROVED_MANAGER',  icon: 'hourglass_top', count: null },
    { label: 'Validés RH (Final)', value: 'APPROVED_RH',      icon: 'verified',      count: null },
    { label: 'Refusés',           value: 'REJECTED',          icon: 'cancel',        count: null },
  ];


  // =========================================================
  // INIT
  // =========================================================

  constructor(
    private authService: AuthService,
    private leaveService: ManagerLeaveService
  ) {}

  ngOnInit(): void {
    this.sub.add(
      this.authService.currentUser$.subscribe(user => {
        if (user?.id) {
          this.managerId = user.id;
          this.loadLeaves();
        } else {
          this.loading = false;
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }


  // =========================================================
  // CHARGEMENT
  // =========================================================

  error = false;

  loadLeaves(): void {
    if (!this.managerId) return;
    this.loading = true;
    this.error = false;

    this.sub.add(
      this.leaveService.getTeamLeaves(this.managerId).subscribe({
        next: (leaves) => {
          this.all = leaves || [];
          this.updateTabCounts();
          this.applyFilter();
          this.loading = false;
          this.error = false;
        },
        error: (err) => {
          console.error('Erreur chargement congés équipe :', err);
          this.all = [];
          this.filtered = [];
          this.updateTabCounts();
          this.loading = false;
          this.error = true;
        }
      })
    );
  }


  // =========================================================
  // FILTRES
  // =========================================================

  setTab(tab: LeaveStatus | 'ALL'): void {
    this.activeTab = tab;
    this.applyFilter();
  }

  private applyFilter(): void {
    if (this.activeTab === 'ALL') {
      this.filtered = [...this.all];
    } else {
      this.filtered = this.all.filter(l => l.status === this.activeTab);
    }
  }

  private updateTabCounts(): void {
    this.tabs.forEach(tab => {
      if (tab.value === 'ALL') {
        tab.count = this.all.length;
      } else {
        tab.count = this.all.filter(l => l.status === tab.value).length;
      }
    });
  }

  get pendingCount(): number {
    return this.all.filter(l => l.status === 'PENDING').length;
  }


  // =========================================================
  // ACTIONS
  // =========================================================

  approve(leave: ManagerLeave): void {
    if (!this.managerId) return;
    this.actionInProgress = true;

    this.sub.add(
      this.leaveService.approve(this.managerId, leave.id).subscribe({
        next: (updated) => {
          this.replaceLeave(updated);
          this.showToast('Congé approuvé avec succès !', 'success', 'check_circle');
          this.actionInProgress = false;
        },
        error: (err) => {
          const msg = err?.error?.message ?? 'Erreur lors de l\'approbation.';
          this.showToast(msg, 'error', 'error');
          this.actionInProgress = false;
        }
      })
    );
  }

  openRejectModal(leave: ManagerLeave): void {
    this.selectedLeave = leave;
    this.rejectReason  = '';
    this.showRejectModal = true;
  }

  closeRejectModal(): void {
    this.showRejectModal = false;
    this.selectedLeave   = null;
  }

  confirmReject(): void {
    if (!this.managerId || !this.selectedLeave) return;
    this.actionInProgress = true;

    this.sub.add(
      this.leaveService.reject(this.managerId, this.selectedLeave.id, this.rejectReason).subscribe({
        next: (updated) => {
          this.replaceLeave(updated);
          this.showToast('Congé refusé.', 'success', 'cancel');
          this.closeRejectModal();
          this.actionInProgress = false;
        },
        error: (err) => {
          const msg = err?.error?.message ?? 'Erreur lors du refus.';
          this.showToast(msg, 'error', 'error');
          this.actionInProgress = false;
        }
      })
    );
  }

  private replaceLeave(updated: ManagerLeave): void {
    const idx = this.all.findIndex(l => l.id === updated.id);
    if (idx !== -1) this.all[idx] = updated;
    this.updateTabCounts();
    this.applyFilter();
  }


  // =========================================================
  // TOAST
  // =========================================================

  private showToast(message: string, type: 'success' | 'error', icon: string): void {
    this.toast = { show: true, message, type, icon };
    setTimeout(() => { this.toast.show = false; }, 3500);
  }


  // =========================================================
  // UTILITAIRES
  // =========================================================

  getInitials(name: string): string {
    const parts = name.split(' ').filter(Boolean);
    return parts.map(p => p[0]).join('').toUpperCase().slice(0, 2);
  }

  formatDate(dateStr?: string): string {
    if (!dateStr) return '—';
    try {
      return new Date(dateStr).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' });
    } catch { return dateStr; }
  }

  getDays(start: string, end: string): number {
    try {
      const ms = new Date(end).getTime() - new Date(start).getTime();
      return Math.ceil(ms / 86400000) + 1;
    } catch { return 0; }
  }

  getTypeLabel(type: string): string {
    const map: Record<string, string> = {
      PAID: 'Payé', SICK: 'Maladie', RTT: 'RTT', OTHER: 'Autre'
    };
    return map[type] ?? type;
  }

  getStatusLabel(status: LeaveStatus): string {
    const map: Record<LeaveStatus, string> = {
      PENDING:          'Nouvelle demande',
      APPROVED_MANAGER: 'En attente RH',
      APPROVED_RH:      'Validé (Final)',
      REJECTED:         'Refusé'
    };
    return map[status] ?? status;
  }
}

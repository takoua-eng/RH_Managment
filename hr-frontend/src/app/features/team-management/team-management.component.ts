import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';

import { EmployeeService } from '../../core/services/employee.service';
import { Employee } from '../../core/models/employee.model';

/** Groupe d'employés par manager */
interface TeamGroup {
  managerId:   number | null;
  managerName: string;
  employees:   Employee[];
}

interface Toast {
  show:    boolean;
  message: string;
  type:    'success' | 'error';
  icon:    string;
}

@Component({
  selector: 'app-team-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './team-management.component.html',
  styleUrl: './team-management.component.scss'
})
export class TeamManagementComponent implements OnInit, OnDestroy {

  // =========================================================
  // ÉTAT
  // =========================================================

  all:        Employee[]  = [];   // tous les employés
  allManagers: Employee[] = [];   // pour le dropdown reassign
  teamGroups:  TeamGroup[] = [];

  loading        = true;
  actionInProgress = false;

  // Drag & drop
  dragging:         Employee | null = null;
  dragOverManagerId: number | null  = null;

  // Modal réaffectation
  showReassignModal   = false;
  reassignTarget:      Employee | null = null;
  selectedNewManagerId: number | null  = null;

  // Toast
  toast: Toast = { show: false, message: '', type: 'success', icon: 'check_circle' };

  private sub = new Subscription();

  constructor(private employeeService: EmployeeService) {}


  // =========================================================
  // INIT
  // =========================================================

  ngOnInit(): void {
    this.loadAll();
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  refreshAll(): void {
    this.loadAll();
  }

  private loadAll(): void {
    this.loading = true;

    // Charger tous les employés (page large)
    this.sub.add(
      this.employeeService.getEmployees(0, 500, '').subscribe({
        next: (page) => {
          this.all = page.content ?? [];
          this.buildGroups();

          // Managers pour le dropdown = tous les employés pour l'instant
          // (le backend filtre, mais on charge tous pour le fallback)
          this.employeeService.getManagers().subscribe({
            next:  (mgrs) => { this.allManagers = mgrs.length ? mgrs : this.all; },
            error: ()     => { this.allManagers = this.all; }
          });

          this.loading = false;
        },
        error: (err) => {
          this.all = [];
          this.allManagers = [];
          this.buildGroups();
          this.showToast('Erreur lors du chargement de l\'équipe.', 'error', 'error');
          this.loading = false;
        }
      })
    );
  }


  // =========================================================
  // CONSTRUCTION GROUPES
  // =========================================================

  private buildGroups(): void {
    // Employés qui sont managers (ils sont référencés comme managerId par d'autres)
    const managerIds = new Set<number>();
    this.all.forEach(e => { if (e.managerId != null) managerIds.add(e.managerId); });

    const groups = new Map<number | null, TeamGroup>();

    // Créer une colonne par manager identifié
    managerIds.forEach(mid => {
      const mgr = this.all.find(e => e.id === mid);
      groups.set(mid, {
        managerId:   mid,
        managerName: mgr ? `${mgr.firstName} ${mgr.lastName}` : `Manager #${mid}`,
        employees:   []
      });
    });

    // Colonne "Non affectés"
    groups.set(null, {
      managerId:   null,
      managerName: 'Non affectés',
      employees:   []
    });

    // Placer chaque employé dans sa colonne
    this.all.forEach(emp => {
      // Ne pas mettre les managers eux-mêmes dans une colonne (sauf s'ils ont un manager)
      const group = groups.get(emp.managerId ?? null);
      if (group) group.employees.push(emp);
    });

    // Convertir en tableau trié (managers en premier, non affectés en dernier)
    this.teamGroups = Array.from(groups.values())
      .sort((a, b) => {
        if (a.managerId === null) return 1;
        if (b.managerId === null) return -1;
        return a.managerName.localeCompare(b.managerName);
      });
  }


  // =========================================================
  // DRAG & DROP
  // =========================================================

  onDragStart(event: DragEvent, emp: Employee): void {
    this.dragging = emp;
    event.dataTransfer?.setData('text/plain', String(emp.id));
  }

  onDragOver(event: DragEvent, managerId: number | null): void {
    event.preventDefault();
    this.dragOverManagerId = managerId;
  }

  onDrop(event: DragEvent, targetManagerId: number | null): void {
    event.preventDefault();
    this.dragOverManagerId = null;

    if (!this.dragging) return;
    if (this.dragging.managerId === targetManagerId) { this.dragging = null; return; }

    this.assignManagerAndRefresh(this.dragging, targetManagerId);
    this.dragging = null;
  }


  // =========================================================
  // MODAL RÉAFFECTATION
  // =========================================================

  openReassignModal(emp: Employee): void {
    this.reassignTarget       = emp;
    this.selectedNewManagerId = emp.managerId ?? null;
    this.showReassignModal    = true;
  }

  closeReassignModal(): void {
    this.showReassignModal = false;
    this.reassignTarget    = null;
  }

  confirmReassign(): void {
    if (!this.reassignTarget) return;
    this.assignManagerAndRefresh(this.reassignTarget, this.selectedNewManagerId);
    this.closeReassignModal();
  }


  // =========================================================
  // APPEL API
  // =========================================================

  private assignManagerAndRefresh(emp: Employee, newManagerId: number | null): void {
    this.actionInProgress = true;

    // Mise à jour optimiste locale
    emp.managerId = newManagerId;
    const newMgr = newManagerId ? this.all.find(e => e.id === newManagerId) : null;
    emp.managerName = newMgr ? `${newMgr.firstName} ${newMgr.lastName}` : null;
    this.buildGroups();

    this.sub.add(
      this.employeeService.assignManager(emp.id, newManagerId).subscribe({
        next: (updated) => {
          // Synchroniser avec la réponse serveur
          const idx = this.all.findIndex(e => e.id === updated.id);
          if (idx !== -1) {
            this.all[idx] = { ...this.all[idx], ...updated };
          }
          this.buildGroups();
          this.showToast('Affectation mise à jour !', 'success', 'check_circle');
          this.actionInProgress = false;
        },
        error: (err) => {
          const msg = err?.error?.message ?? 'Erreur lors de la réaffectation.';
          this.showToast(msg, 'error', 'error');
          this.loadAll(); // rollback
          this.actionInProgress = false;
        }
      })
    );
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
    const parts = (name ?? '').trim().split(' ').filter(Boolean);
    return parts.map(p => p[0]).join('').toUpperCase().slice(0, 2) || '??';
  }
}

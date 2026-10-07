import { Component, OnInit, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LeaveService } from '../../core/services/leave.service';
import { EmployeeService } from '../../core/services/employee.service';
import {
  LeaveRequest, Employee, LEAVE_STATUS, LEAVE_TYPE, LeaveStatus, LeaveType
} from '../../core/models/interfaces';
import { MatDialogModule, MatDialog, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { Observable, BehaviorSubject, forkJoin, of } from 'rxjs';
import { switchMap, tap, map, catchError } from 'rxjs/operators';

export interface CalendarLeaveItem {
  id?: number;
  employeeId?: number;
  employeeName: string;
  name: string;
  type: string;
  startDate?: string;
  endDate?: string;
  daysCount?: number;
  status?: string;
  reason?: string;
}

interface CalendarDay {
  dayNumber: number | null;
  dateStr: string | null;
  leaves: CalendarLeaveItem[];
}

// ===================================================================
// Fenêtre de confirmation (inchangée)
// ===================================================================
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div style="padding: 20px; font-family: system-ui, -apple-system, sans-serif;">
      <h4 style="margin-top: 0; font-weight: bold; font-size: 1.1rem; color: #1e1e1e;">Confirmation</h4>
      <p style="margin: 10px 0 20px 0; color: #555; font-size: 0.9rem;">{{ data.message }}</p>
      <div style="display: flex; justify-content: flex-end; gap: 8px;">
        <button (click)="dialogRef.close(false)" class="fluent-btn-secondary" style="padding: 6px 14px; font-size: 0.85rem; border-radius: 4px; border: 1px solid #ccc; background: white; cursor: pointer;">Annuler</button>
        <button (click)="dialogRef.close(true)" class="fluent-btn-primary" style="padding: 6px 14px; font-size: 0.85rem; border-radius: 4px; border: none; background: #0078d4; color: white; cursor: pointer;">Confirmer</button>
      </div>
    </div>
  `
})
export class ConfirmDialogComponent {
  constructor(
    public dialogRef: MatDialogRef<ConfirmDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { message: string }
  ) {}
}

// ===================================================================
// Fenêtre de refus : le motif est obligatoire
// ===================================================================
@Component({
  selector: 'app-reject-reason-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div style="padding: 20px; font-family: system-ui, -apple-system, sans-serif; min-width: 320px;">
      <h4 style="margin-top: 0; font-weight: bold; font-size: 1.1rem; color: #1e1e1e;">Refuser la demande</h4>
      <p style="margin: 8px 0; color: #555; font-size: 0.9rem;">Indiquez le motif du refus (il sera communiqué à l'employé) :</p>
      <textarea [(ngModel)]="motif" rows="4" maxlength="1000"
                style="width: 100%; box-sizing: border-box; padding: 8px; border: 1px solid #ccc; border-radius: 4px; font-family: inherit; font-size: 0.9rem;"
                placeholder="Ex. : période de forte activité, équipe déjà en sous-effectif…"></textarea>
      <div style="display: flex; justify-content: flex-end; gap: 8px; margin-top: 14px;">
        <button (click)="dialogRef.close(null)" style="padding: 6px 14px; font-size: 0.85rem; border-radius: 4px; border: 1px solid #ccc; background: white; cursor: pointer;">Annuler</button>
        <button (click)="dialogRef.close(motif.trim())" [disabled]="!motif.trim()"
                style="padding: 6px 14px; font-size: 0.85rem; border-radius: 4px; border: none; background: #dc2626; color: white; cursor: pointer;">Refuser</button>
      </div>
    </div>
  `
})
export class RejectReasonDialogComponent {
  motif = '';
  constructor(public dialogRef: MatDialogRef<RejectReasonDialogComponent>) {}
}

// ===================================================================
// Page des congés (admin / RH)
// ===================================================================
@Component({
  selector: 'app-leave',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatSnackBarModule],
  templateUrl: './leave.component.html',
  styleUrl: './leave.component.scss'
})
export class LeaveComponent implements OnInit {
  // Dictionnaires partagés, utilisables dans le template
  readonly LEAVE_STATUS = LEAVE_STATUS;
  readonly LEAVE_TYPE = LEAVE_TYPE;

  leaveRequests$: Observable<LeaveRequest[]> = new Observable<LeaveRequest[]>();
  employees: Employee[] = [];
  selectedEmployeeId: number | null = null;
  pendingCount: number | null = null;
  absentToday: number | null = null;
  approvedThisMonth: number | null = null;
  presenceRate: number | null = null;
  loadingStats = true;
  filterStatus: 'ALL' | 'PENDING' = 'ALL';

  // Refresh mechanism (BehaviorSubject ensures template subscription triggers load)
  refresh$ = new BehaviorSubject<void>(undefined);

  // Dynamic calendar fields
  currentYear = new Date().getFullYear();
  currentMonth = new Date().getMonth() + 1;
  absenceMap = new Map<string, CalendarLeaveItem[]>();
  todayStr = new Date().toISOString().split('T')[0];

  weekDays = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];
  calendarDays: CalendarDay[] = [];

  // Leave Details Modal State
  showDetailsModal = false;
  selectedLeaveDetails: CalendarLeaveItem | LeaveRequest | null = null;

  // Submit Request Modal State
  showRequestModal = false;
  newRequest: Partial<LeaveRequest> = {
    employeeName: '',
    startDate: '',
    endDate: '',
    type: 'Payé',
    reason: ''
  };

  allLeavesCache: LeaveRequest[] = [];

  constructor(
    private leaveService: LeaveService,
    private employeeService: EmployeeService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    // Pipeline réactif : toutes les demandes de congé (consultation admin / RH)
    this.leaveRequests$ = this.refresh$.pipe(
      switchMap(() => {
        this.loadingStats = true;
        return forkJoin([
          this.leaveService.getStats().pipe(catchError(() => of(null as any))),
          this.leaveService.getAllLeaves().pipe(catchError(() => of(this.leaveService.getLeaves())))
        ]);
      }),
      tap(([stats, allLeaves]) => {
        const leavesList = allLeaves || [];
        this.allLeavesCache = leavesList;

        // Calcul dynamique des indicateurs
        this.computeStatsFromLeaves(leavesList, stats);

        // Calendrier construit à partir de toutes les demandes
        this.buildAbsenceMapFromLeaves(leavesList);
      }),
      map(([stats, allLeaves]) => {
        if (this.filterStatus === 'PENDING') {
          return (allLeaves || []).filter(l => l.status === 'PENDING');
        }
        return allLeaves || [];
      })
    );

    this.loadEmployeesList();
  }

  loadEmployeesList(): void {
    this.employeeService.getEmployees(0, 1000, '').subscribe({
      next: (res: any) => {
        if (res && res.content) {
          this.employees = res.content;
        } else if (Array.isArray(res)) {
          this.employees = res;
        }
        // Recalcule les indicateurs avec le vrai nombre d'employés
        if (this.allLeavesCache.length > 0) {
          this.computeStatsFromLeaves(this.allLeavesCache);
        }
      },
      error: (err) => console.error('Error loading employees for leave modal:', err)
    });
  }

  computeStatsFromLeaves(allLeaves: LeaveRequest[], backendStats?: any): void {
    if (!allLeaves) return;

    // 1. Demandes en attente de la décision du manager
    const pending = allLeaves.filter(l => l.status === 'PENDING').length;

    // 2. Absents aujourd'hui (congés approuvés couvrant la date du jour)
    const today = new Date();
    const y = today.getFullYear();
    const m = String(today.getMonth() + 1).padStart(2, '0');
    const d = String(today.getDate()).padStart(2, '0');
    const todayStr = `${y}-${m}-${d}`;

    const approvedLeaves = allLeaves.filter(l => l.status === 'APPROVED');

    const absentTodaySet = new Set<string | number>();
    approvedLeaves.forEach(l => {
      if (l.startDate && l.endDate && todayStr >= l.startDate && todayStr <= l.endDate) {
        // compté par employé (un employé avec deux congés ne compte qu'une fois)
        absentTodaySet.add(l.employeeId ?? l.employeeName ?? l.id);
      }
    });
    const absentToday = absentTodaySet.size;

    // 3. Congés approuvés ce mois-ci
    const currentYearMonth = `${y}-${m}`;
    const approvedThisMonth = approvedLeaves.filter(l => {
      const sPrefix = l.startDate ? l.startDate.substring(0, 7) : '';
      const ePrefix = l.endDate ? l.endDate.substring(0, 7) : '';
      return sPrefix === currentYearMonth || ePrefix === currentYearMonth;
    }).length;

    // 4. Taux de présence : uniquement à partir du vrai nombre d'employés
    const totalEmployees = this.employees ? this.employees.length : 0;
    const rate: number | null = totalEmployees > 0
      ? Math.max(0, Math.min(100, Math.round((1 - (absentToday / totalEmployees)) * 100)))
      : null;

    // Valeurs du backend si elles sont valides, sinon valeurs calculées
    this.pendingCount = (backendStats && typeof backendStats.pendingCount === 'number' && backendStats.pendingCount >= 0)
      ? backendStats.pendingCount
      : pending;

    this.absentToday = (backendStats && typeof backendStats.absentToday === 'number' && backendStats.absentToday >= 0)
      ? backendStats.absentToday
      : absentToday;

    this.approvedThisMonth = (backendStats && typeof backendStats.approvedThisMonth === 'number' && backendStats.approvedThisMonth >= 0)
      ? backendStats.approvedThisMonth
      : approvedThisMonth;

    this.presenceRate = (backendStats && typeof backendStats.presenceRate === 'number' && backendStats.presenceRate >= 0)
      ? backendStats.presenceRate
      : rate;

    this.loadingStats = false;
  }

  setFilterStatus(status: 'ALL' | 'PENDING'): void {
    this.filterStatus = status;
    this.refresh$.next();
  }

  buildAbsenceMapFromLeaves(allLeaves: LeaveRequest[]): void {
    const map = new Map<string, CalendarLeaveItem[]>();

        allLeaves.forEach(leave => {
      if (leave.status === 'REJECTED' || leave.status === 'CANCELLED') return;   // ← ajout
      if (!leave.startDate || !leave.endDate) return;

      const sParts = leave.startDate.split('-').map(Number);
      const eParts = leave.endDate.split('-').map(Number);

      if (sParts.length !== 3 || eParts.length !== 3) return;

      const current = new Date(sParts[0], sParts[1] - 1, sParts[2]);
      const end = new Date(eParts[0], eParts[1] - 1, eParts[2]);

      if (isNaN(current.getTime()) || isNaN(end.getTime())) return;

      while (current <= end) {
        const y = current.getFullYear();
        const m = String(current.getMonth() + 1).padStart(2, '0');
        const d = String(current.getDate()).padStart(2, '0');
        const dateKey = `${y}-${m}-${d}`;

        if (!map.has(dateKey)) {
          map.set(dateKey, []);
        }

        map.get(dateKey)!.push({
          id: leave.id,
          employeeId: leave.employeeId,
          employeeName: leave.employeeName || 'Employé',
          name: leave.employeeName || 'Employé',
          type: leave.type,
          startDate: leave.startDate,
          endDate: leave.endDate,
          daysCount: this.calculateDaysCount(leave),
          status: leave.status,
          reason: leave.reason
        });

        current.setDate(current.getDate() + 1);
      }
    });

    this.absenceMap = map;
    this.generateCalendar();

    // Complète avec les absences du calendrier renvoyées par le backend
    this.loadCalendarAbsences();
  }

  loadCalendarAbsences(): void {
    this.leaveService.getCalendarAbsences(this.currentYear, this.currentMonth).subscribe({
      next: (absences) => {
        if (absences && absences.length > 0) {
          absences.forEach(abs => {
            if (abs.dates) {
              abs.dates.forEach(date => {
                if (!this.absenceMap.has(date)) {
                  this.absenceMap.set(date, []);
                }
                const existing = this.absenceMap.get(date)!;
                const exists = existing.some(item => item.id === abs.id || (item.name === abs.employeeName && item.startDate === abs.startDate));
                if (!exists) {
                  existing.push({
                    id: abs.id,
                    employeeId: abs.employeeId,
                    employeeName: abs.employeeName,
                    name: abs.employeeName,
                    type: abs.type,
                    startDate: abs.startDate,
                    endDate: abs.endDate,
                    daysCount: abs.daysCount,
                    status: abs.status,
                    reason: abs.reason
                  });
                }
              });
            }
          });
        }
        this.generateCalendar();
      },
      error: () => {
        this.generateCalendar();
      }
    });
  }

  openLeaveDetails(leave: CalendarLeaveItem | LeaveRequest, event?: MouseEvent): void {
    if (event) {
      event.stopPropagation();
    }
    this.selectedLeaveDetails = leave;
    this.showDetailsModal = true;
  }

  calculateDaysCount(leave: any): number {
    if (leave?.daysCount) {
      return leave.daysCount;
    }
    if (leave?.startDate && leave?.endDate) {
      const start = new Date(leave.startDate);
      const end = new Date(leave.endDate);
      const diffTime = Math.abs(end.getTime() - start.getTime());
      return Math.ceil(diffTime / (1000 * 60 * 60 * 24)) + 1;
    }
    return 1;
  }

  getMonthName(monthNum: number): string {
    const months = [
      'Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin',
      'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'
    ];
    return months[monthNum - 1];
  }

  previousMonth(): void {
    if (this.currentMonth === 1) {
      this.currentMonth = 12;
      this.currentYear--;
    } else {
      this.currentMonth--;
    }
    this.generateCalendar();
    this.loadCalendarAbsences();
  }

  nextMonth(): void {
    if (this.currentMonth === 12) {
      this.currentMonth = 1;
      this.currentYear++;
    } else {
      this.currentMonth++;
    }
    this.generateCalendar();
    this.loadCalendarAbsences();
  }

  getLeaveBadgeClass(type: string): string {
    switch (type?.toUpperCase()) {
      case 'PAID':
      case 'ANNUAL':
      case 'PAYÉ':
        return 'primary';
      case 'SICK':
      case 'MALADIE':
        return 'error';
      case 'RTT':
      case 'OTHER':
        return 'warning';
      default:
        return 'secondary';
    }
  }

  getLeaveTypeClass(type: string): string {
    switch (type?.toUpperCase()) {
      case 'PAID':
      case 'ANNUAL':
      case 'PAYÉ':
        return 'c-paye';
      case 'SICK':
      case 'MALADIE':
        return 'c-maladie';
      case 'RTT':
      case 'OTHER':
        return 'c-rtt';
      default:
        return 'c-sans-solde';
    }
  }

  /** Libellé du type : ANNUAL → "Congé annuel", SICK → "Maladie", OTHER → "Autre". */
  getLeaveTypeLabel(type: string): string {
    const key = (type || '').toUpperCase() as LeaveType;
    return LEAVE_TYPE[key] ?? type ?? 'Autre';
  }

  /** Libellé du statut : PENDING → "En attente", APPROVED → "Approuvé"… */
  getStatusLabel(status: string): string {
    return LEAVE_STATUS[status as LeaveStatus]?.label ?? status ?? 'Inconnu';
  }

  /** Classe CSS du badge de statut (classes existantes de la page). */
  getStatusBadgeClass(status: string): string {
    switch (status) {
      case 'APPROVED':
        return 'success';
      case 'PENDING':
        return 'warning';
      case 'REJECTED':
        return 'error';
      default:
        return 'secondary';
    }
  }

  /**
   * Approbation par l'admin. Le backend ne l'autorise que si l'employé n'a pas de manager ;
   * sinon, il renvoie un message expliquant que seul le manager peut décider.
   */
  approve(id: number) {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '350px',
      data: { message: 'Voulez-vous vraiment approuver cette demande de congé ?' }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.leaveService.approveLeaveDecision(id).subscribe({
          next: () => {
            this.snackBar.open('Demande de congé approuvée avec succès', 'Fermer', { duration: 3000 });
            this.refresh$.next();
            this.loadCalendarAbsences();
          },
          error: (err) => {
            const msg = err.error?.message || 'Erreur lors de l\'approbation de la demande';
            this.snackBar.open(msg, 'Fermer', { duration: 5000 });
            console.error('Error approving leave:', err);
          }
        });
      }
    });
  }

  /** Refus par l'admin, avec un motif obligatoire. */
  reject(id: number) {
    const dialogRef = this.dialog.open(RejectReasonDialogComponent, { width: '420px' });

    dialogRef.afterClosed().subscribe((motif: string | null) => {
      if (motif) {
        this.leaveService.rejectLeaveDecision(id, motif).subscribe({
          next: () => {
            this.snackBar.open('Demande de congé refusée', 'Fermer', { duration: 3000 });
            this.refresh$.next();
            this.loadCalendarAbsences();
          },
          error: (err) => {
            const msg = err.error?.message || 'Erreur lors du refus de la demande';
            this.snackBar.open(msg, 'Fermer', { duration: 5000 });
            console.error('Error rejecting leave:', err);
          }
        });
      }
    });
  }

  openRequestModal() {
    this.newRequest = {
      employeeName: '',
      startDate: new Date().toISOString().split('T')[0],
      endDate: new Date().toISOString().split('T')[0],
      type: 'Payé',
      reason: ''
    };
    this.selectedEmployeeId = this.employees.length > 0 ? this.employees[0].id : null;
    this.showRequestModal = true;
  }

  submitRequest() {
    if (!this.selectedEmployeeId) {
      this.snackBar.open('Veuillez sélectionner un employé', 'Fermer', { duration: 3000 });
      return;
    }

    let backendType: LeaveType = 'ANNUAL';
    if (this.newRequest.type === 'Maladie') {
      backendType = 'SICK';
    } else if (this.newRequest.type === 'RTT' || this.newRequest.type === 'Sans solde') {
      backendType = 'OTHER';
    }

    const payload = {
      employeeId: this.selectedEmployeeId,
      startDate: this.newRequest.startDate || '',
      endDate: this.newRequest.endDate || '',
      type: backendType,
      reason: this.newRequest.reason || ''
    };

    this.leaveService.requestLeave(payload).subscribe({
      next: () => {
        this.snackBar.open('Demande de congé enregistrée : en attente de la décision du manager', 'Fermer', { duration: 3000 });
        this.showRequestModal = false;
        this.refresh$.next();
        this.loadCalendarAbsences();
      },
      error: (err) => {
        const errorMsg = err.error?.message || err.message || 'Erreur serveur';
        this.snackBar.open('Erreur lors de l\'enregistrement: ' + errorMsg, 'Fermer', { duration: 5000 });
        console.error('Error requesting leave:', err);
      }
    });
  }

  private generateCalendar() {
    const monthDaysCount = new Date(this.currentYear, this.currentMonth, 0).getDate();
    const firstDay = new Date(this.currentYear, this.currentMonth - 1, 1).getDay();
    const startOffset = firstDay === 0 ? 6 : firstDay - 1;

    const days: CalendarDay[] = [];

    for (let i = 0; i < startOffset; i++) {
      days.push({ dayNumber: null, dateStr: null, leaves: [] });
    }

    for (let day = 1; day <= monthDaysCount; day++) {
      const dateStr = `${this.currentYear}-${this.currentMonth.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}`;
      const activeLeaves = this.absenceMap.get(dateStr) || [];

      days.push({
        dayNumber: day,
        dateStr,
        leaves: activeLeaves
      });
    }

    this.calendarDays = days;
  }
}
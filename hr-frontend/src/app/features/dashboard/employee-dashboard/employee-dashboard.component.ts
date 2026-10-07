import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';

import { AuthService } from '../../../core/services/auth.service';
import { EmployeeDashboardService } from '../../../core/services/employee-dashboard.service';
import {
  EmployeeDashboard,
  LEAVE_STATUS,
  LEAVE_TYPE,
  LeaveStatus,
  LeaveType
} from '../../../core/models/interfaces';

interface CalendarCell {
  day: number | null;
  status: 'APPROVED' | 'PENDING' | null;
  isToday: boolean;
  isWeekend: boolean;
}

@Component({
  selector: 'app-employee-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './employee-dashboard.component.html',
  styleUrl: './employee-dashboard.component.scss'
})
export class EmployeeDashboardComponent implements OnInit, OnDestroy {

  /** Routes de l'espace employé */
  readonly routes = {
    leaves: '/dashboard/leave',
    trainings: '/dashboard/training'
  };

  readonly weekDays = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];
  readonly today = new Date();
    private subs = new Subscription();
  // Données réelles, venant uniquement du backend
  data: EmployeeDashboard | null = null;
  loading = true;
  error = '';

  // Photo de l'utilisateur connecté (même source que l'ancien bandeau)
  photo: string | null = null;

  // Mini-calendrier du mois en cours
  calendar: CalendarCell[] = [];
  monthLabel = '';

  constructor(
    private dashboardService: EmployeeDashboardService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.subs.add(
      this.authService.currentUser$.subscribe(user => this.photo = user?.photo || null)
    );

    // 1. Affichage immédiat des dernières données connues (cache de session)
    this.subs.add(
      this.dashboardService.data$.subscribe(d => {
        if (d) {
          this.data = d;
          this.buildCalendar(d.monthLeaves || []);
          this.loading = false;
        }
      })
    );

    // 2. Rafraîchissement en arrière-plan
    this.load();
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }

  /** Recharge depuis le backend. Le spinner ne s'affiche que s'il n'y a encore aucune donnée. */
  load(): void {
    this.error = '';
    this.loading = !this.data;

    this.dashboardService.refresh().subscribe({
      error: (err) => {
        // On garde les données déjà affichées ; on signale l'erreur seulement s'il n'y en a pas
        if (!this.data) {
          this.error = err.error?.message || 'Impossible de charger votre tableau de bord.';
        }
        this.loading = false;
      }
    });
  }

  // =========================================================
  // AFFICHAGE
  // =========================================================

  initials(first?: string | null, last?: string | null): string {
    return ((first?.[0] || '') + (last?.[0] || '')).toUpperCase() || '?';
  }

  /** ANNUAL → "Congé annuel", SICK → "Maladie", OTHER → "Autre" */
  typeLabel(type: string): string {
    return LEAVE_TYPE[type as LeaveType] ?? type;
  }

  /** PENDING → "En attente", APPROVED → "Approuvé", REJECTED → "Refusé" */
  statusLabel(status: string): string {
    return LEAVE_STATUS[status as LeaveStatus]?.label ?? status;
  }

  statusClass(status: string): string {
    return LEAVE_STATUS[status as LeaveStatus]?.css ?? 'badge-gris';
  }

  /** "à l'instant", "il y a 5 min", "il y a 3 h", "hier", "il y a 6 jours", "il y a 2 semaines" */
  relativeTime(iso: string): string {
    const diffMin = Math.floor((Date.now() - new Date(iso).getTime()) / 60000);
    if (diffMin < 1) return "à l'instant";
    if (diffMin < 60) return `il y a ${diffMin} min`;
    const h = Math.floor(diffMin / 60);
    if (h < 24) return `il y a ${h} h`;
    const d = Math.floor(h / 24);
    if (d === 1) return 'hier';
    if (d < 7) return `il y a ${d} jours`;
    if (d < 30) {
      const w = Math.floor(d / 7);
      return `il y a ${w} semaine${w > 1 ? 's' : ''}`;
    }
    return `il y a ${Math.floor(d / 30)} mois`;
  }

  trackById(_: number, item: { id: number }): number {
    return item.id;
  }

  trackByIndex(index: number): number {
    return index;
  }

  // =========================================================
  // MINI-CALENDRIER DU MOIS EN COURS
  // =========================================================

  private buildCalendar(leaves: { startDate: string; endDate: string; status: string }[]): void {
    const year = this.today.getFullYear();
    const month = this.today.getMonth();
    this.monthLabel = this.today.toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });

    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const firstDay = new Date(year, month, 1).getDay();   // 0 = dimanche
    const offset = firstDay === 0 ? 6 : firstDay - 1;     // semaine commençant le lundi

    const cells: CalendarCell[] = [];
    for (let i = 0; i < offset; i++) {
      cells.push({ day: null, status: null, isToday: false, isWeekend: false });
    }

    for (let day = 1; day <= daysInMonth; day++) {
      const date = `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
      const leave = leaves.find(l => date >= l.startDate && date <= l.endDate);
      const weekDay = new Date(year, month, day).getDay();

      cells.push({
        day,
        status: leave ? (leave.status as 'APPROVED' | 'PENDING') : null,
        isToday: day === this.today.getDate(),
        isWeekend: weekDay === 0 || weekDay === 6
      });
    }

    this.calendar = cells;
  }
}
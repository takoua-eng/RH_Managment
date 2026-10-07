import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { NgApexchartsModule } from 'ng-apexcharts';

import { AuthService, UserSession } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { EmployeeService } from '../../core/services/employee.service';
import {
  ManagerDashboardService,
  ManagerDashboardData,
  ManagerNotification
} from '../../core/services/manager-dashboard.service';
import {
  ManagerDashboardStatsDTO,
  ManagerInterviewDetailsDto,
  ManagerCandidateToProcessDto,
  ManagerPendingFeedbackDto,
  ManagerMyEvaluationsStats
} from '../../core/models/interfaces';

interface LocalNotification {
  id: number;
  text: string;
  time: string;
  icon: string;
  unread: boolean;
  type?: string;
  link?: string;
}

interface QuickAction {
  label: string;
  icon: string;
  path: string;
  color: string;
}

export interface AgendaGroup {
  dayLabel: string;
  interviews: ManagerInterviewDetailsDto[];
}

@Component({
  selector: 'app-manager-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, NgApexchartsModule],
  templateUrl: './manager-dashboard.component.html',
  styleUrl: './manager-dashboard.component.scss'
})
export class ManagerDashboardComponent implements OnInit, OnDestroy {

  // =========================================================
  // ÉTAT ÉQUIPE & NOTIFICATIONS
  // =========================================================

  currentUser: UserSession | null = null;
  dashboardData: ManagerDashboardData | null = null;
  loading = true;

  localNotifications: LocalNotification[] = [];

  quickActions: QuickAction[] = [
    { label: 'Valider les congés',  icon: 'task_alt',      path: '/manager/leaves',      color: '#f59e0b' },
    { label: 'Mon Équipe',          icon: 'group',          path: '/manager/team',        color: '#3b82f6' },
    { label: 'Formations',          icon: 'school',         path: '/manager/training',    color: '#10b981' },
    { label: 'Évaluations',         icon: 'star',           path: '/manager/evaluations', color: '#8b5cf6' },
    { label: 'Mes entretiens',      icon: 'calendar_month', path: '/manager/interviews', color: '#6366f1' },
    { label: 'Candidats à évaluer', icon: 'person_search',  path: '/manager/recruitment', color: '#ec4899' },
    { label: 'Mon Profil',          icon: 'person',         path: '/manager/profile',     color: '#64748b' },
    { label: 'Paramètres',          icon: 'settings',       path: '/manager/settings',    color: '#94a3b8' },
  ];

  // =========================================================
  // ÉTAT RECRUTEMENT
  // =========================================================

  recruitmentStats: ManagerDashboardStatsDTO | null = null;
  recruitmentLoading = true;
  recruitmentError = false;

  agendaGroups: AgendaGroup[] = [];
  nextInterviewCountdown = '';
  evaluationChartOptions: any = null;

  private countdownInterval: any = null;
  private sub = new Subscription();


  // =========================================================
  // CONSTRUCTEUR
  // =========================================================

  constructor(
    private authService: AuthService,
    private managerDashboardService: ManagerDashboardService,
    private notificationService: NotificationService,
    private employeeService: EmployeeService,
    private router: Router
  ) {}


  // =========================================================
  // INITIALISATION
  // =========================================================

  ngOnInit(): void {
    // 1. Chargement utilisateur & dashboard équipe
    this.sub.add(
      this.authService.currentUser$.subscribe(user => {
        this.currentUser = user;

        if (user?.id) {
          this.loadDashboard(user.id);
        } else {
          this.sub.add(
            this.employeeService.getCurrentEmployee().subscribe({
              next: (emp) => {
                if (emp && emp.id) {
                  this.loadDashboard(emp.id);
                } else {
                  this.loading = false;
                }
              },
              error: () => {
                this.loading = false;
              }
            })
          );
        }
      })
    );

    // 2. Chargement unique des stats de recrutement
    this.loadRecruitmentStats();

    // 3. Minuteur du compte à rebours (créé une seule fois, mis à jour toutes les 60s)
    this.countdownInterval = setInterval(() => {
      this.updateNextInterviewCountdown();
    }, 60000);
  }


  // =========================================================
  // DESTRUCTION
  // =========================================================

  ngOnDestroy(): void {
    this.sub.unsubscribe();
    if (this.countdownInterval) {
      clearInterval(this.countdownInterval);
    }
  }


  // =========================================================
  // CHARGEMENT STATS RECRUTEMENT
  // =========================================================

  loadRecruitmentStats(): void {
    this.recruitmentLoading = true;
    this.recruitmentError = false;

    this.sub.add(
      this.managerDashboardService.getManagerDashboardStats().subscribe({
        next: (stats) => {
          this.recruitmentStats = stats;
          this.recruitmentLoading = false;
          this.recruitmentError = false;

          // Traitement des données calculées une seule fois
          this.updateNextInterviewCountdown();
          this.buildAgendaGroups(stats.upcomingInterviews || []);
          this.setupEvaluationChart(stats.myEvaluations);
        },
        error: (err) => {
          console.error('Impossible de charger les statistiques de recrutement manager :', err);
          this.recruitmentLoading = false;
          this.recruitmentError = true;
        }
      })
    );
  }


  // =========================================================
  // TRAITEMENT DU COMPTE À REBOURS
  // =========================================================

  private updateNextInterviewCountdown(): void {
    const nextIt = this.recruitmentStats?.nextInterview;
    if (!nextIt || !nextIt.date || !nextIt.time) {
      this.nextInterviewCountdown = '';
      return;
    }

    const now = new Date();
    const interviewDateTime = new Date(`${nextIt.date}T${nextIt.time}:00`);

    if (isNaN(interviewDateTime.getTime())) {
      this.nextInterviewCountdown = '';
      return;
    }

    const diffMs = interviewDateTime.getTime() - now.getTime();
    const isToday = now.toDateString() === interviewDateTime.toDateString();

    const tomorrow = new Date(now);
    tomorrow.setDate(tomorrow.getDate() + 1);
    const isTomorrow = tomorrow.toDateString() === interviewDateTime.toDateString();

    if (diffMs <= 0) {
      if (isToday) {
        this.nextInterviewCountdown = `aujourd'hui à ${nextIt.time}`;
      } else {
        this.nextInterviewCountdown = `passé`;
      }
      return;
    }

    const diffMins = Math.floor(diffMs / 60000);
    const hours = Math.floor(diffMins / 60);
    const mins = diffMins % 60;

    if (isToday) {
      if (hours === 0) {
        this.nextInterviewCountdown = `dans ${mins} min`;
      } else {
        this.nextInterviewCountdown = `dans ${hours} h ${mins > 0 ? (mins < 10 ? '0' + mins : mins) : '00'}`;
      }
      return;
    }

    if (isTomorrow) {
      this.nextInterviewCountdown = `demain à ${nextIt.time}`;
      return;
    }

    const days = Math.ceil(diffMs / (1000 * 60 * 60 * 24));
    this.nextInterviewCountdown = `dans ${days} jours`;
  }


  // =========================================================
  // REGROUPEMENT DE L'AGENDA (7 PROCHAINS JOURS)
  // =========================================================

  private buildAgendaGroups(interviews: ManagerInterviewDetailsDto[]): void {
    if (!interviews || interviews.length === 0) {
      this.agendaGroups = [];
      return;
    }

    const map = new Map<string, ManagerInterviewDetailsDto[]>();
    interviews.forEach(item => {
      const key = item.date;
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(item);
    });

    const now = new Date();
    const todayStr = now.toISOString().split('T')[0];
    const tomorrow = new Date(now);
    tomorrow.setDate(tomorrow.getDate() + 1);
    const tomorrowStr = tomorrow.toISOString().split('T')[0];

    const groups: AgendaGroup[] = [];
    map.forEach((items, dateStr) => {
      let dayLabel = '';
      if (dateStr === todayStr) {
        dayLabel = "Aujourd'hui";
      } else if (dateStr === tomorrowStr) {
        dayLabel = 'Demain';
      } else {
        const d = new Date(dateStr + 'T00:00:00');
        if (!isNaN(d.getTime())) {
          dayLabel = d.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });
          dayLabel = dayLabel.charAt(0).toUpperCase() + dayLabel.slice(1);
        } else {
          dayLabel = dateStr;
        }
      }
      groups.push({ dayLabel, interviews: items });
    });

    this.agendaGroups = groups;
  }


  // =========================================================
  // OPTION APEXCHARTS POUR LES ÉVALUATIONS
  // =========================================================

  private setupEvaluationChart(myEvaluations: ManagerMyEvaluationsStats | undefined): void {
    const favorable = myEvaluations?.favorable || 0;
    const reserve = myEvaluations?.reserve || 0;
    const defavorable = myEvaluations?.defavorable || 0;

    this.evaluationChartOptions = {
      series: [favorable, reserve, defavorable],
      chart: {
        type: 'donut',
        height: 220
      },
      labels: ['Favorable', 'Réservé', 'Défavorable'],
      colors: ['#10b981', '#f59e0b', '#ef4444'],
      legend: {
        position: 'bottom',
        fontSize: '12px'
      },
      dataLabels: {
        enabled: true,
        formatter: (val: number) => Math.round(val) + '%'
      },
      plotOptions: {
        pie: {
          donut: {
            size: '65%'
          }
        }
      }
    };
  }


  // =========================================================
  // CHARGEMENT DASHBOARD ÉQUIPE
  // =========================================================

  private loadDashboard(managerId: number): void {
    this.loading = true;

    this.sub.add(
      this.managerDashboardService.getDashboard(managerId).subscribe({
        next: (data) => {
          this.dashboardData = data;
          this.buildNotificationsFromApi(data?.recentNotifications || []);
          this.loading = false;
        },
        error: (err) => {
          console.error('Impossible de charger le dashboard manager :', err);
          this.dashboardData = null;
          this.localNotifications = [];
          this.loading = false;
        }
      })
    );
  }


  // =========================================================
  // CONSTRUCTION DES NOTIFICATIONS (AVEC TEMPS RELATIF CORRIGÉ)
  // =========================================================

  private buildNotificationsFromApi(apiNotifs: ManagerNotification[]): void {
    if (!apiNotifs || apiNotifs.length === 0) {
      this.localNotifications = [];
      return;
    }

    this.localNotifications = apiNotifs.map((n, i) => ({
      id: n.id || (i + 1),
      text: n.text,
      time: this.formatNotificationTime(n.time),
      icon: n.icon || 'notifications',
      unread: n.unread,
      link: (n as any).link,
      type: (n as any).type
    }));
  }

  private formatNotificationTime(rawTime: string): string {
    if (!rawTime) return '';
    let clean = rawTime.replace(/\(s\)/gi, '').trim();
    clean = clean.replace(/1\s+semaines/gi, '1 semaine');
    clean = clean.replace(/1\s+jours/gi, '1 jour');
    return clean;
  }


  // =========================================================
  // HELPERS FORMATTAGE & TRACKBY
  // =========================================================

  get unreadNotificationsCount(): number {
    return this.localNotifications.filter(n => n.unread).length;
  }

  getTopTraining(): string {
    if (!this.dashboardData?.topTrainings) return 'Aucune';
    const keys = Object.keys(this.dashboardData.topTrainings);
    if (keys.length === 0) return 'Aucune';
    return keys[0];
  }

  formatAiScore(score: number | null | undefined): string {
    if (score == null || isNaN(score)) return '—';
    const pct = score > 1 ? Math.round(score) : Math.round(score * 100);
    return `${pct} %`;
  }

  formatPercent(val: number | null | undefined): string {
    if (val == null || isNaN(val)) return '—';
    const pct = val > 1 ? Math.round(val) : Math.round(val * 100);
    return `${pct} %`;
  }

  getStars(rating: number | null | undefined): ('full' | 'half' | 'empty')[] {
    const stars: ('full' | 'half' | 'empty')[] = [];
    const val = rating ?? 0;
    for (let i = 1; i <= 5; i++) {
      if (val >= i) {
        stars.push('full');
      } else if (val >= i - 0.5) {
        stars.push('half');
      } else {
        stars.push('empty');
      }
    }
    return stars;
  }

  trackByCandidate(index: number, candidate: ManagerCandidateToProcessDto): number {
    return candidate.candidateId;
  }

  trackByInterview(index: number, item: ManagerInterviewDetailsDto | ManagerPendingFeedbackDto): number {
    return item.interviewId;
  }

  trackByGroup(index: number, group: AgendaGroup): string {
    return group.dayLabel;
  }


  getMemberInitials(member: { firstName?: string; lastName?: string } | null | undefined): string {
    if (!member) return 'EMP';
    const f = member.firstName?.[0] ?? '';
    const l = member.lastName?.[0] ?? '';
    return (f + l).toUpperCase() || 'EMP';
  }

  onImgError(event: Event): void {
    (event.target as HTMLImageElement).style.display = 'none';
  }

  // =========================================================
  // GESTION DES NOTIFICATIONS
  // =========================================================

  markAllNotificationsRead(): void {
    this.localNotifications.forEach(n => (n.unread = false));
    if (this.currentUser?.id) {
      this.sub.add(
        this.notificationService.markAllAsRead(this.currentUser.id).subscribe({
          error: (err) => console.error('Erreur lors du marquage des notifications:', err)
        })
      );
    }
  }

  markNotificationRead(notification: LocalNotification): void {
    const navigateToTarget = () => {
      if (notification.link) {
        this.router.navigateByUrl(notification.link);
      } else if (notification.icon === 'calendar_today') {
        this.router.navigate(['/manager/leaves']);
      } else if (notification.icon === 'school') {
        this.router.navigate(['/manager/team-trainings']);
      } else if (notification.icon === 'star') {
        this.router.navigate(['/manager/evaluations']);
      } else if (notification.icon === 'groups') {
        this.router.navigate(['/manager/team']);
      } else if (notification.icon === 'person_search' || notification.icon === 'assignment_ind') {
        this.router.navigate(['/manager/recruitment']);
      } else if (notification.icon === 'event') {
        this.router.navigate(['/manager/interviews']);
      }
    };

    if (!notification.unread) {
      navigateToTarget();
      return;
    }

    notification.unread = false;
    if (this.currentUser?.id && notification.id) {
      this.sub.add(
        this.notificationService.markAsRead(this.currentUser.id, notification.id).subscribe({
          next: () => navigateToTarget(),
          error: (err) => {
            console.error('Erreur lors du marquage de la notification:', err);
            navigateToTarget();
          }
        })
      );
    } else {
      navigateToTarget();
    }
  }
}

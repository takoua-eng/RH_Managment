import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { NgApexchartsModule } from 'ng-apexcharts';
import { DashboardService } from '../../core/services/dashboard.service';
import { DepartmentService } from '../../core/services/department.service';
import { AuthService } from '../../core/services/auth.service';
import { ToastNotificationService } from '../../core/services/toast-notification.service';
import { 
  DashboardStatsDTO, 
  Department 
} from '../../core/models/interfaces';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, NgApexchartsModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  stats: DashboardStatsDTO | null = null;
  departments: Department[] = [];

  // Filter state
  selectedMonths: number = 6;
  selectedDepartment: string = '';

  loading = true;
  errorMessage = '';

  // Tab state for Actions Required
  activeActionTab: 'unprocessed' | 'aiErrors' | 'missingFeedbacks' | 'expiringOffers' = 'unprocessed';

  // Relaunching IA analysis state
  relaunchingMap: { [candidateId: number]: boolean } = {};

  // ApexCharts Configurations
  public recruitmentChartOptions: any;
  public leaveChartOptions: any;
  public aiRecommendationChartOptions: any;
  public scoreDistributionChartOptions: any;
  public missingSkillsChartOptions: any;

  constructor(
    private dashboardService: DashboardService,
    private departmentService: DepartmentService,
    private authService: AuthService,
    private toastService: ToastNotificationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    const isAdmin = this.authService.isAdmin();
    const isRH = this.authService.isRH();
    const isManager = this.authService.isManager();
    const isEmp = !isAdmin && !isRH && !isManager;

    if (isManager && !isAdmin && !isRH) {
      this.router.navigate(['/manager/dashboard']);
      return;
    }

    if (isEmp) {
      this.router.navigate(['/dashboard/employee']);
      return;
    }

    this.loadDepartments();
    this.loadStats();
  }

  loadDepartments(): void {
    this.departmentService.getAll().subscribe({
      next: (data) => this.departments = data,
      error: (err) => console.error('Erreur chargement départements:', err)
    });
  }

  loadStats(): void {
    this.loading = true;
    this.errorMessage = '';

    this.dashboardService.getDashboardStats(this.selectedMonths, this.selectedDepartment).subscribe({
      next: (data) => {
        this.stats = data;
        this.initCharts(data);
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement statistiques dashboard:', err);
        this.errorMessage = err.error?.message || "Impossible de charger les données du tableau de bord.";
        this.loading = false;
      }
    });
  }

  onFilterChange(): void {
    this.loadStats();
  }

  relaunchAnalysis(candidateId: number): void {
    this.relaunchingMap[candidateId] = true;
    this.dashboardService.relaunchAiAnalysis(candidateId).subscribe({
      next: () => {
        this.toastService.success("Relance de l'analyse IA effectuée avec succès.");
        setTimeout(() => {
          this.relaunchingMap[candidateId] = false;
          this.loadStats();
        }, 3000);
      },
      error: (err) => {
        console.error('Erreur relance analyse IA:', err);
        this.toastService.error("Erreur lors de la relance de l'analyse IA.");
        this.relaunchingMap[candidateId] = false;
      }
    });
  }

  exportPdf(): void {
    window.print();
  }

  // Formatting helpers
  formatScorePercent(score: number | null | undefined): string {
    if (score == null) return '—';
    return `${Math.round(score * 100)} %`;
  }

  formatVal(val: number | null | undefined): string {
    if (val == null) return '—';
    return val.toString();
  }

  getApplicationsTrend(): { percent: number; isPositive: boolean; text: string } {
    if (!this.stats || !this.stats.kpis) return { percent: 0, isPositive: true, text: 'N/A' };
    const curr = this.stats.kpis.applicationsThisMonth || 0;
    const prev = this.stats.kpis.applicationsLastMonth || 0;
    if (prev === 0) {
      return { percent: curr > 0 ? 100 : 0, isPositive: true, text: curr > 0 ? '+100%' : '0%' };
    }
    const diff = ((curr - prev) / prev) * 100;
    const percent = Math.abs(Math.round(diff));
    const isPositive = diff >= 0;
    const text = `${isPositive ? '+' : '-'}${percent}%`;
    return { percent, isPositive, text };
  }

  getFunnelPercentage(current: number, previous: number): string {
    if (!previous || previous === 0) return '100 %';
    const pct = Math.round((current / previous) * 100);
    return `${pct} %`;
  }

  getAgreementPercent(rate: number | null | undefined): string {
    if (rate == null) return '0';
    return (Math.round(rate * 1000) / 10).toString();
  }

  get totalActionsCount(): number {
    if (!this.stats || !this.stats.actionsRequired) return 0;
    const ar = this.stats.actionsRequired;
    return (ar.totalUnprocessedCount || 0) +
           (ar.totalAiErrorsCount || 0) +
           (ar.totalMissingFeedbacksCount || 0) +
           (ar.totalExpiringOffersCount || 0);
  }

  private initCharts(data: DashboardStatsDTO): void {
    this.initRecruitmentChart(data.monthlyTrend);
    this.initLeaveChart(data.leavesByDepartment);
    this.initAiRecommendationChart(data.aiRecommendations);
    this.initScoreDistributionChart(data.aiScoreDistribution);
    this.initMissingSkillsChart(data.topMissingSkills);
  }

  private initRecruitmentChart(monthlyTrend: any[]): void {
    const categories = monthlyTrend.map(m => m.label);
    const series1 = monthlyTrend.map(m => m.applications);
    const series2 = monthlyTrend.map(m => m.hired);

    this.recruitmentChartOptions = {
      series: [
        { name: "Candidatures Reçues", data: series1 },
        { name: "Candidats Embauchés", data: series2 }
      ],
      chart: {
        height: 300,
        type: "line",
        fontFamily: "'Segoe UI', 'Inter', sans-serif",
        toolbar: { show: false },
        background: 'transparent'
      },
      colors: ["#2563EB", "#10B981"],
      stroke: { width: 3, curve: "smooth" },
      dataLabels: { enabled: false },
      xaxis: {
        categories: categories,
        labels: { style: { colors: "#64748B" } }
      },
      grid: {
        borderColor: "rgba(148, 163, 184, 0.15)",
        strokeDashArray: 3
      },
      tooltip: { theme: 'light' }
    };
  }

  private initLeaveChart(leavesByDept: any[]): void {
    const categories = leavesByDept.map(d => d.departmentName);
    const data = leavesByDept.map(d => d.leavesCount);

    this.leaveChartOptions = {
      series: [{ name: "Demandes de Congé", data: data }],
      chart: {
        height: 300,
        type: "bar",
        fontFamily: "'Segoe UI', 'Inter', sans-serif",
        toolbar: { show: false },
        background: 'transparent'
      },
      colors: ["#6366F1"],
      plotOptions: { bar: { columnWidth: "45%", borderRadius: 6 } },
      dataLabels: { enabled: false },
      xaxis: {
        categories: categories,
        labels: { style: { colors: "#64748B" } }
      },
      grid: {
        borderColor: "rgba(148, 163, 184, 0.15)",
        strokeDashArray: 3
      }
    };
  }

  private initAiRecommendationChart(aiRec: any): void {
    const series = [
      aiRec.compatible || 0,
      aiRec.aExaminer || 0,
      aiRec.nonCompatible || 0,
      aiRec.enAttente || 0,
      aiRec.erreur || 0
    ];

    this.aiRecommendationChartOptions = {
      series: series,
      chart: {
        type: "donut",
        height: 280,
        fontFamily: "'Segoe UI', 'Inter', sans-serif"
      },
      labels: ["Compatible", "À examiner", "Non compatible", "En attente", "Erreur"],
      colors: ["#10B981", "#F59E0B", "#EF4444", "#9CA3AF", "#991B1B"],
      legend: { position: "bottom" },
      dataLabels: { enabled: true }
    };
  }

  private initScoreDistributionChart(scoreDist: any[]): void {
    const categories = scoreDist.map(s => s.range);
    const data = scoreDist.map(s => s.count);

    this.scoreDistributionChartOptions = {
      series: [{ name: "Candidatures", data: data }],
      chart: {
        type: "bar",
        height: 280,
        fontFamily: "'Segoe UI', 'Inter', sans-serif",
        toolbar: { show: false }
      },
      colors: ["#3B82F6"],
      plotOptions: { bar: { columnWidth: "60%", borderRadius: 4 } },
      dataLabels: { enabled: false },
      xaxis: {
        categories: categories,
        labels: { style: { colors: "#64748B", fontSize: '10px' } }
      },
      grid: { borderColor: "rgba(148, 163, 184, 0.15)" }
    };
  }

  private initMissingSkillsChart(missingSkills: any[]): void {
    const categories = missingSkills.map(s => s.skill);
    const data = missingSkills.map(s => s.count);

    this.missingSkillsChartOptions = {
      series: [{ name: "Manquante (fois)", data: data }],
      chart: {
        type: "bar",
        height: 280,
        fontFamily: "'Segoe UI', 'Inter', sans-serif",
        toolbar: { show: false }
      },
      colors: ["#F97316"],
      plotOptions: { bar: { horizontal: true, barHeight: "60%", borderRadius: 4 } },
      dataLabels: { enabled: true, style: { colors: ['#ffffff'] } },
      xaxis: {
        categories: categories,
        labels: { style: { colors: "#64748B" } }
      },
      grid: { borderColor: "rgba(148, 163, 184, 0.15)" }
    };
  }
}

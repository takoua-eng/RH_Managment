
import { Component, OnInit, ViewChild, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgApexchartsModule, ChartComponent } from 'ng-apexcharts';
import { Subscription } from 'rxjs';
import { EvaluationService } from '../../core/services/evaluation.service';
import { AuthService, UserSession } from '../../core/services/auth.service';
import { EmployeeService } from '../../core/services/employee.service';
import { Employee, Evaluation } from '../../core/models/interfaces';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';

@Component({
  selector: 'app-evaluations',
  standalone: true,
  imports: [CommonModule, FormsModule, NgApexchartsModule, MatSnackBarModule],
  templateUrl: './evaluations.component.html',
  styleUrl: './evaluations.component.scss'
})
export class EvaluationsComponent implements OnInit, OnDestroy {
  @ViewChild('chart') chart!: ChartComponent;

  userSession: UserSession | null = null;
  private authSub!: Subscription;
  private empSub!: Subscription;

  isManager = false;
  isEmployeeOnly = false;
  currentEmployeeId: number | null = null;

  employees: Employee[] = [];
  selectedEmployeeId: number | null = null;
  activeEmployee: Employee | null = null;

  evaluations: Evaluation[] = [];
  employeeHistory: Evaluation[] = [];
  
  radarChartOptions: any = null;
  showModal = false;
  loading = true;
  actionInProgress = false;
  errorMessage = '';

  criterias = [
    { key: 'communication', label: 'Communication' },
    { key: 'leadership', label: 'Leadership' },
    { key: 'technical', label: 'Technique' },
    { key: 'teamwork', label: 'Travail d\'équipe' },
    { key: 'productivity', label: 'Productivité' }
  ];

  newEval: Partial<Evaluation> = {
    employeeName: '',
    communication: 8,
    leadership: 5,
    technical: 8,
    teamwork: 8,
    productivity: 8,
    comments: ''
  };

  constructor(
    private evaluationService: EvaluationService,
    private authService: AuthService,
    private employeeService: EmployeeService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.authSub = this.authService.currentUser$.subscribe(session => {
      this.userSession = session;
      if (session) {
        this.isManager = session.role === 'Manager' || session.role === 'Administrateur';
        this.isEmployeeOnly = session.role === 'Employé';
        this.currentEmployeeId = session.id || null;

        if (this.isManager) {
          this.loadEmployees();
        } else if (this.isEmployeeOnly && this.currentEmployeeId) {
          this.loadEmployeeEvaluations(this.currentEmployeeId);
        } else {
           this.loading = false;
        }
      }
    });
  }

  ngOnDestroy(): void {
    if (this.authSub) this.authSub.unsubscribe();
    if (this.empSub) this.empSub.unsubscribe();
  }

  loadEmployees(): void {
    if (this.currentEmployeeId) {
      this.empSub = this.employeeService.getManagerTeam(this.currentEmployeeId).subscribe({
        next: (data) => {
          this.employees = data || [];
          if (this.employees.length > 0) {
             if (!this.selectedEmployeeId) {
                this.selectedEmployeeId = this.employees[0].id;
             }
          }
          this.loadManagerEvaluations(this.currentEmployeeId!);
        },
        error: (err) => {
          console.error('Erreur chargement équipe', err);
          this.errorMessage = 'Erreur lors du chargement de l\'équipe.';
          this.loading = false;
        }
      });
    } else {
       this.loading = false;
    }
  }

  loadManagerEvaluations(managerId: number) {
    this.loading = true;
    this.errorMessage = '';
    this.evaluationService.getManagerEvaluations(managerId).subscribe({
      next: (data) => {
        this.evaluations = data;
        this.onEmployeeChange();
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement évaluations', err);
        this.errorMessage = 'Erreur lors du chargement des évaluations.';
        this.loading = false;
      }
    });
  }

  loadEmployeeEvaluations(employeeId: number) {
    this.loading = true;
    this.errorMessage = '';
    this.evaluationService.getEmployeeEvaluations(employeeId).subscribe({
      next: (data) => {
        this.evaluations = data;
        this.employeeHistory = data;
        this.initRadarChart();
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement évaluations', err);
        this.errorMessage = 'Erreur lors du chargement de vos évaluations.';
        this.loading = false;
      }
    });
  }

  onEmployeeChange() {
    if (this.isManager && this.selectedEmployeeId) {
      this.activeEmployee = this.employees.find(e => e.id === Number(this.selectedEmployeeId)) || null;
      this.applyHistoryFilter();
      this.initRadarChart();
    }
  }

  applyHistoryFilter() {
    if (this.activeEmployee) {
      // Filter by ID
      this.employeeHistory = this.evaluations.filter(e => e.employeeId === this.activeEmployee!.id)
                                             .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
    } else {
      this.employeeHistory = [];
    }
  }

  getAverageScore(evalObj: Evaluation): number {
    return evalObj.communication + evalObj.leadership + evalObj.technical + evalObj.teamwork + evalObj.productivity;
  }

  getCriteriaValue(key: string): number {
    return (this.newEval as any)[key] || 5;
  }

  setCriteriaValue(key: string, val: number) {
    (this.newEval as any)[key] = Number(val);
  }

  openEvaluationModal() {
    this.newEval = {
      employeeId: this.activeEmployee?.id,
      employeeName: this.activeEmployee ? `${this.activeEmployee.firstName} ${this.activeEmployee.lastName || this.activeEmployee.name}` : '',
      managerId: this.currentEmployeeId || undefined,
      communication: 8,
      leadership: 6,
      technical: 8,
      teamwork: 8,
      productivity: 8,
      comments: ''
    };
    this.showModal = true;
  }

  submitEvaluation() {
    this.actionInProgress = true;
    
    // Add date for payload
    const payload = {
      ...this.newEval,
      date: new Date().toISOString().split('T')[0]
    };

    this.evaluationService.createEvaluation(payload).subscribe({
      next: (res) => {
        this.snackBar.open('Évaluation enregistrée avec succès', 'Fermer', { duration: 3000 });
        this.evaluations.push(res);
        this.showModal = false;
        this.actionInProgress = false;
        this.onEmployeeChange();
      },
      error: (err) => {
        this.snackBar.open('Erreur lors de l\'enregistrement', 'Fermer', { duration: 3000 });
        console.error(err);
        this.actionInProgress = false;
      }
    });
  }

  private initRadarChart() {
    let comm = 0, lead = 0, tech = 0, team = 0, prod = 0;
    
    if (this.employeeHistory.length > 0) {
      const latest = this.employeeHistory[0];
      comm = latest.communication;
      lead = latest.leadership;
      tech = latest.technical;
      team = latest.teamwork;
      prod = latest.productivity;
    }

    if (this.employeeHistory.length === 0 && !this.isManager) {
        // If employee has no evals, show empty chart
        comm = 0; lead = 0; tech = 0; team = 0; prod = 0;
    } else if (this.employeeHistory.length === 0 && this.isManager) {
        // Defaults for UI when no eval
        comm = 7; lead = 5; tech = 7; team = 7; prod = 7;
    }

    this.radarChartOptions = {
      series: [
        {
          name: "Dernière évaluation",
          data: [comm, lead, tech, team, prod]
        }
      ],
      chart: {
        height: 280,
        type: "radar",
        fontFamily: "'Segoe UI', 'Inter', sans-serif",
        toolbar: { show: false },
        background: 'transparent'
      },
      colors: ["#2563EB"],
      stroke: { width: 2 },
      fill: { opacity: 0.2 },
      markers: { size: 4 },
      xaxis: {
        categories: ["Communication", "Leadership", "Technique", "Travail équipe", "Productivité"],
        labels: {
          style: {
            colors: ["#888888", "#888888", "#888888", "#888888", "#888888"],
            fontSize: "12px"
          }
        }
      },
      yaxis: { min: 0, max: 10, tickAmount: 5 },
      grid: { borderColor: "rgba(128, 128, 128, 0.15)" }
    };
  }
}


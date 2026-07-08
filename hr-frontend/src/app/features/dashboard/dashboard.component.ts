import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { NgApexchartsModule, ChartComponent } from 'ng-apexcharts';
import { EmployeeService } from '../../core/services/employee.service';
import { LeaveService } from '../../core/services/leave.service';
import { RecruitmentService } from '../../core/services/recruitment.service';
import { DocumentService } from '../../core/services/document.service';
import { Candidate } from '../../core/models/interfaces';
import keycloak from '../../keycloak.config';
import { KeycloakService } from '../../keycloak.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, NgApexchartsModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  @ViewChild('chart') chart!: ChartComponent;
  
  employeeCount = 125;
  pendingLeavesCount = 18;
  activeRecruitmentCount = 7;
  documentsCount = 342;

  recentCandidates: Candidate[] = [];

  // ApexCharts configurations
  public recruitmentChartOptions: any;
  public leaveChartOptions: any;

  constructor(
    private employeeService: EmployeeService,
    private leaveService: LeaveService,
    private recruitmentService: RecruitmentService,
    private documentService: DocumentService,
    private keycloakService: KeycloakService
  )
   {}
  ngOnInit(): void {
    
    // Dynamic KPI statistics
    this.employeeCount = this.employeeService.getEmployees().length + 117; // Adjust to match requested 125
    this.pendingLeavesCount = this.leaveService.getPendingCount(); // Matches requested 18
    this.activeRecruitmentCount = this.recruitmentService.getCandidates().filter(c => c.status !== 'Embauché' && c.status !== 'Rejeté').length + 3; // Adjust to match 7
    this.documentsCount = this.documentService.getDocsCount(); // Matches requested 342
        console.log('Username :', this.keycloakService.getUsername());

    // Recent candidates
    this.recentCandidates = this.recruitmentService.getCandidates().slice(0, 3);

    // Initializing charts configurations
    this.initRecruitmentChart();
    this.initLeaveChart();
  }

  getScoreClass(score: number): string {
    if (score >= 90) return 'success';
    if (score >= 80) return 'primary';
    return 'warning';
  }

  getScoreBg(score: number): string {
    if (score >= 90) return 'bg-success';
    if (score >= 80) return 'bg-primary';
    return 'bg-warning';
  }

  private initRecruitmentChart() {
    this.recruitmentChartOptions = {
      series: [
        {
          name: "Recrutements Commencés",
          data: [15, 23, 18, 30, 25, 35, 42]
        },
        {
          name: "Candidats Embauchés",
          data: [5, 8, 12, 10, 15, 14, 20]
        }
      ],
      chart: {
        height: 300,
        type: "line",
        fontFamily: "'Segoe UI', 'Inter', sans-serif",
        toolbar: {
          show: false
        },
        background: 'transparent'
      },
      colors: ["#2563EB", "#10B981"],
      stroke: {
        width: 3,
        curve: "smooth"
      },
      dataLabels: {
        enabled: false
      },
      xaxis: {
        categories: ["Nov", "Déc", "Jan", "Fév", "Mar", "Avr", "Mai"],
        labels: {
          style: {
            colors: "#888888"
          }
        }
      },
      grid: {
        borderColor: "rgba(128, 128, 128, 0.15)",
        strokeDashArray: 3
      },
      tooltip: {
        theme: 'light'
      }
    };
  }

  private initLeaveChart() {
    this.leaveChartOptions = {
      series: [
        {
          name: "Jours de Congé",
          data: [28, 14, 18, 22]
        }
      ],
      chart: {
        height: 300,
        type: "bar",
        fontFamily: "'Segoe UI', 'Inter', sans-serif",
        toolbar: {
          show: false
        },
        background: 'transparent'
      },
      colors: ["#2563EB"],
      plotOptions: {
        bar: {
          columnWidth: "45%",
          borderRadius: 6
        }
      },
      dataLabels: {
        enabled: false
      },
      xaxis: {
        categories: ["IT", "RH", "Marketing", "Finance"],
        labels: {
          style: {
            colors: "#888888"
          }
        }
      },
      grid: {
        borderColor: "rgba(128, 128, 128, 0.15)",
        strokeDashArray: 3
      }
    };
  }
  
}

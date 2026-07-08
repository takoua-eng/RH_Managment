import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgApexchartsModule, ChartComponent } from 'ng-apexcharts';
import { EmployeeService } from '../../core/services/employee.service';
import { Employee, Evaluation } from '../../core/models/interfaces';

@Component({
  selector: 'app-evaluations',
  standalone: true,
  imports: [CommonModule, FormsModule, NgApexchartsModule],
  templateUrl: './evaluations.component.html',
  styleUrl: './evaluations.component.scss'
})
export class EvaluationsComponent implements OnInit {
  @ViewChild('chart') chart!: ChartComponent;

  employees: Employee[] = [];
  selectedEmployeeId = 1;
  activeEmployee: Employee | null = null;

  evaluations: Evaluation[] = [
    {
      id: 1,
      employeeName: 'Ahmed Alami',
      date: '2026-05-10',
      communication: 9,
      leadership: 7,
      technical: 10,
      teamwork: 9,
      productivity: 9,
      comments: 'Ahmed est un élément technique incontournable. Très rigoureux, il doit juste continuer à travailler sur la communication transverse en dehors de l\'équipe technique.'
    },
    {
      id: 2,
      employeeName: 'Sara Benjelloun',
      date: '2026-05-15',
      communication: 9,
      leadership: 6,
      technical: 9,
      teamwork: 10,
      productivity: 9,
      comments: 'Excellente intégration de Sara. Son autonomie sur Angular et son attitude collaborative ont grandement fluidifié les projets récents.'
    },
    {
      id: 3,
      employeeName: 'Marc Dubois',
      date: '2026-03-22',
      communication: 10,
      leadership: 10,
      technical: 7,
      teamwork: 9,
      productivity: 8,
      comments: 'Marc fait preuve d\'un excellent leadership et pilote avec brio les ressources humaines.'
    }
  ];

  employeeHistory: Evaluation[] = [];
  radarChartOptions: any = null;
  showModal = false;

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

  constructor(private employeeService: EmployeeService) {}

  ngOnInit(): void {
    // Load from local storage if existing
    const saved = localStorage.getItem('hr_evaluations');
    if (saved) {
      this.evaluations = JSON.parse(saved);
    } else {
      localStorage.setItem('hr_evaluations', JSON.stringify(this.evaluations));
    }

    this.employeeService.employees$.subscribe(data => {
      this.employees = data;
      if (this.employees.length > 0) {
        // Match default employee selection
        const first = this.employees[0];
        this.selectedEmployeeId = first.id;
        this.onEmployeeChange();
      }
    });
  }

  onEmployeeChange() {
    this.activeEmployee = this.employees.find(e => e.id === Number(this.selectedEmployeeId)) || null;
    this.applyHistoryFilter();
    this.initRadarChart();
  }

  applyHistoryFilter() {
    if (this.activeEmployee) {
      const name = `${this.activeEmployee.firstName} ${this.activeEmployee.name}`;
      this.employeeHistory = this.evaluations.filter(e => e.employeeName.toLowerCase().includes(this.activeEmployee!.name.toLowerCase()));
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
      employeeName: this.activeEmployee ? `${this.activeEmployee.firstName} ${this.activeEmployee.name}` : '',
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
    const nextId = this.evaluations.length > 0 ? Math.max(...this.evaluations.map(e => e.id)) + 1 : 1;
    const finalEval: Evaluation = {
      id: nextId,
      employeeName: this.newEval.employeeName || '',
      date: new Date().toISOString().split('T')[0],
      communication: this.newEval.communication || 5,
      leadership: this.newEval.leadership || 5,
      technical: this.newEval.technical || 5,
      teamwork: this.newEval.teamwork || 5,
      productivity: this.newEval.productivity || 5,
      comments: this.newEval.comments || ''
    };

    this.evaluations.push(finalEval);
    localStorage.setItem('hr_evaluations', JSON.stringify(this.evaluations));
    this.showModal = false;
    this.onEmployeeChange();
  }

  private initRadarChart() {
    // If there is an evaluation for the active employee, use it. Else use defaults.
    let comm = 7, lead = 5, tech = 7, team = 7, prod = 7;
    
    if (this.employeeHistory.length > 0) {
      const latest = this.employeeHistory[0];
      comm = latest.communication;
      lead = latest.leadership;
      tech = latest.technical;
      team = latest.teamwork;
      prod = latest.productivity;
    }

    this.radarChartOptions = {
      series: [
        {
          name: "Compétences",
          data: [comm, lead, tech, team, prod]
        }
      ],
      chart: {
        height: 280,
        type: "radar",
        fontFamily: "'Segoe UI', 'Inter', sans-serif",
        toolbar: {
          show: false
        },
        background: 'transparent'
      },
      colors: ["#2563EB"],
      stroke: {
        width: 2
      },
      fill: {
        opacity: 0.2
      },
      markers: {
        size: 4
      },
      xaxis: {
        categories: ["Communication", "Leadership", "Technique", "Travail équipe", "Productivité"],
        labels: {
          style: {
            colors: ["#888888", "#888888", "#888888", "#888888", "#888888"],
            fontSize: "12px"
          }
        }
      },
      grid: {
        borderColor: "rgba(128, 128, 128, 0.15)"
      }
    };
  }
}

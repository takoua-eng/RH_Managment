import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EmployeeService } from '../../core/services/employee.service';
import { Department, Employee } from '../../core/models/interfaces';

@Component({
  selector: 'app-departments',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="departments-page">
      <!-- Section Header -->
      <div class="row mb-4 align-items-center">
        <div class="col-12">
          <h2 class="fw-bold mb-1">Départements</h2>
          <p class="text-muted mb-0">Découvrez la structure organisationnelle, les responsables et budgets alloués par division.</p>
        </div>
      </div>

      <!-- Departments Grid -->
      <div class="row g-4 mb-5">
        <div class="col-md-6" *ngFor="let dept of departments">
          <div class="fluent-card interactive d-flex flex-column h-100" (click)="selectDepartment(dept.name)">
            <div class="d-flex justify-content-between align-items-start mb-3">
              <div>
                <span class="fluent-badge primary mb-2">{{ dept.name }}</span>
                <h3 class="fw-bold mb-1 fs-5">{{ getFullDepartmentName(dept.name) }}</h3>
              </div>
              <div class="dept-icon-box">
                <span class="material-symbols-outlined">{{ getDeptIcon(dept.name) }}</span>
              </div>
            </div>

            <p class="text-muted fs-7 flex-grow-1">{{ dept.description }}</p>

            <div class="row border-top pt-3 mt-3 g-2 text-center text-sm-start">
              <div class="col-sm-4">
                <span class="d-block text-muted fs-8 text-uppercase fw-semibold">Collaborateurs</span>
                <span class="fs-6 fw-bold text-primary">{{ dept.employeesCount }} employés</span>
              </div>
              <div class="col-sm-4">
                <span class="d-block text-muted fs-8 text-uppercase fw-semibold">Responsable</span>
                <span class="fs-7 fw-semibold text-truncate d-block">{{ dept.manager }}</span>
              </div>
              <div class="col-sm-4">
                <span class="d-block text-muted fs-8 text-uppercase fw-semibold">Budget Annuel</span>
                <span class="fs-6 fw-bold text-success">{{ dept.budget }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Department Detail Panel (Drawer) -->
      <div class="fluent-modal-overlay" *ngIf="showDeptDrawer" (click)="showDeptDrawer = false">
        <div class="detail-panel" (click)="$event.stopPropagation()">
          <div class="detail-header d-flex justify-content-between align-items-center">
            <div>
              <span class="fluent-badge primary mb-1">{{ selectedDeptName }}</span>
              <h4 class="fw-bold mb-0 fs-5">Liste des collaborateurs</h4>
            </div>
            <button (click)="showDeptDrawer = false" class="border-0 bg-transparent text-muted cursor-pointer">
              <span class="material-symbols-outlined">close</span>
            </button>
          </div>
          
          <div class="detail-body mt-3">
            <div class="list-group list-group-flush">
              <div class="list-group-item bg-transparent px-0 py-3 border-bottom d-flex align-items-center gap-3" 
                   *ngFor="let emp of deptEmployees">
                <img [src]="emp.photo" alt="Photo" class="emp-photo">
                <div class="flex-grow-1 overflow-hidden">
                  <h6 class="fw-bold mb-0 text-truncate">{{ emp.firstName }} {{ emp.name }}</h6>
                  <span class="fs-8 text-muted text-truncate d-block">{{ emp.position }}</span>
                </div>
                <span class="fluent-badge" 
                      [ngClass]="{
                        'success': emp.status === 'Actif',
                        'warning': emp.status === 'Congé',
                        'error': emp.status === 'Suspendu'
                      }">{{ emp.status }}</span>
              </div>
            </div>
            
            <div class="empty-panel text-center py-5 text-muted" *ngIf="deptEmployees.length === 0">
              <span class="material-symbols-outlined fs-1 d-block mb-2">group_off</span>
              Aucun employé enregistré dans ce département de démo.
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .dept-icon-box {
      width: 48px;
      height: 48px;
      border-radius: 12px;
      background-color: var(--fluent-primary-light);
      color: var(--fluent-primary);
      display: flex;
      align-items: center;
      justify-content: center;
      
      span {
        font-size: 1.75rem;
      }
    }
    .fs-7 {
      font-size: 0.85rem;
    }
    .fs-8 {
      font-size: 0.725rem;
    }
    .cursor-pointer {
      cursor: pointer;
    }
    .emp-photo {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      object-fit: cover;
      border: 1px solid var(--fluent-border);
    }
    
    /* Fluent Modal slide-over panel */
    .fluent-modal-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background-color: rgba(0, 0, 0, 0.4);
      backdrop-filter: blur(4px);
      z-index: 2000;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .detail-panel {
      position: absolute;
      right: 0;
      top: 0;
      bottom: 0;
      width: 100%;
      max-width: 420px;
      background-color: var(--fluent-surface);
      box-shadow: var(--fluent-shadow-lg);
      border-left: 1px solid var(--fluent-border);
      padding: 1.5rem;
      display: flex;
      flex-direction: column;
      animation: slideInRight 0.3s cubic-bezier(0.16, 1, 0.3, 1);
      overflow-y: auto;
      
      .detail-header {
        border-bottom: 1px solid var(--fluent-border);
        padding-bottom: 1rem;
      }
    }
    @keyframes slideInRight {
      from { transform: translateX(100%); }
      to { transform: translateX(0); }
    }
  `]
})
export class DepartmentsComponent implements OnInit {
  departments: Department[] = [];
  deptEmployees: Employee[] = [];
  selectedDeptName = '';
  showDeptDrawer = false;

  constructor(private employeeService: EmployeeService) {}

  ngOnInit(): void {
    this.employeeService.departments$.subscribe(data => {
      this.departments = data;
    });
  }

  getFullDepartmentName(code: string): string {
    switch (code) {
      case 'IT': return 'Technologies de l\'Information';
      case 'RH': return 'Ressources Humaines';
      case 'Marketing': return 'Marketing & Communication';
      case 'Finance': return 'Finance & Comptabilité';
      default: return code;
    }
  }

  getDeptIcon(code: string): string {
    switch (code) {
      case 'IT': return 'terminal';
      case 'RH': return 'diversity_1';
      case 'Marketing': return 'campaign';
      case 'Finance': return 'payments';
      default: return 'corporate_fare';
    }
  }

  selectDepartment(deptName: string) {
    this.selectedDeptName = deptName;
    this.deptEmployees = this.employeeService.getEmployees().filter(e => e.department === deptName);
    this.showDeptDrawer = true;
  }
}

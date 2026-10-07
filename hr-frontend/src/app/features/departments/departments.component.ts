import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DepartmentService } from '../../core/services/department.service';
import { EmployeeService } from '../../core/services/employee.service';
import { Department, Employee, DepartmentRequest } from '../../core/models/interfaces';
import { ConfirmDialogService } from '../../core/services/confirm-dialog.service';
import { ToastNotificationService } from '../../core/services/toast-notification.service';

export interface DepartmentFormErrors {
  name?: string;
  location?: string;
  managerId?: string;
  budget?: string;
  description?: string;
}

@Component({
  selector: 'app-departments',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './departments.component.html',
  styleUrl: './departments.component.scss'
})
export class DepartmentsComponent implements OnInit {
  departments: Department[] = [];
  filteredDepartments: Department[] = [];

  searchText = '';
  selectedLocation = '';

  governorates: string[] = [
    'Ariana', 'Béja', 'Ben Arous', 'Bizerte', 'Gabès', 'Gafsa', 
    'Jendouba', 'Kairouan', 'Kasserine', 'Kébili', 'Kef', 'Mahdia', 
    'Manouba', 'Médenine', 'Monastir', 'Nabeul', 'Sfax', 'Sidi Bouzid', 
    'Siliana', 'Sousse', 'Tataouine', 'Tozeur', 'Tunis', 'Zaghouan'
  ];

  // Form Modal state
  showFormModal = false;
  formMode: 'add' | 'edit' = 'add';
  currentFormDepartment: Partial<Department> = {};

  // Form Validation state
  formErrors: DepartmentFormErrors = {};
  touchedFields: { [key in keyof DepartmentFormErrors]?: boolean } = {};
  submitted = false;

  // Details panel state
  showDetailPanel = false;
  selectedDepartment: Department | null = null;

  // Manager lists
  allEmployees: Employee[] = [];
  availableManagers: Employee[] = [];
  currentManagerNonRole: Employee | null = null;

  loading = true;
  error = '';

  constructor(
    private departmentService: DepartmentService,
    private employeeService: EmployeeService,
    private confirmService: ConfirmDialogService,
    private toastService: ToastNotificationService
  ) {}

  ngOnInit(): void {
    this.loadDepartments();
    this.loadEmployees();
  }

  loadDepartments(): void {
    this.loading = true;
    this.departmentService.getAll().subscribe({
      next: (data) => {
        this.departments = data.map(dept => ({
          ...dept,
          managerName: dept.manager ? `${dept.manager.firstName} ${dept.manager.lastName}` : 'Non assigné'
        }));
        this.applyFilters();
        this.loading = false;
      },
      error: (err) => {
        console.error('Error loading departments', err);
        this.error = 'Erreur lors du chargement des départements';
        this.toastService.error('Erreur lors du chargement des départements');
        this.loading = false;
      }
    });
  }

  loadEmployees(): void {
    this.employeeService.getEmployees(0, 1000, '').subscribe({
      next: (res) => {
        if (res && res.content) {
          this.allEmployees = res.content;
          // Filtre availableManagers pour ne garder que les employés dont le rôle est MANAGER (ou rôle/position manager)
          this.availableManagers = res.content.filter((e: Employee) => 
            e.role === 'MANAGER' || e.role === 'ROLE_MANAGER' || (e.position && e.position.toLowerCase().includes('manager'))
          );
        }
      },
      error: (err) => {
        console.error('Error loading employees for managers', err);
      }
    });
  }

  applyFilters(): void {
    this.filteredDepartments = this.departments.filter(dept => {
      const matchesSearch = !this.searchText ||
        (dept.name && dept.name.toLowerCase().includes(this.searchText.toLowerCase())) ||
        (dept.managerName && dept.managerName.toLowerCase().includes(this.searchText.toLowerCase())) ||
        (dept.location && dept.location.toLowerCase().includes(this.searchText.toLowerCase()));

      const matchesLocation = !this.selectedLocation || 
        (dept.location && dept.location.trim().toLowerCase() === this.selectedLocation.trim().toLowerCase());

      return matchesSearch && matchesLocation;
    });
  }

  onSearchChange(): void {
    this.applyFilters();
  }

  viewDepartment(dept: Department): void {
    this.selectedDepartment = dept;
    this.showDetailPanel = true;
  }

  closeDetailPanel(): void {
    this.showDetailPanel = false;
    this.selectedDepartment = null;
  }

  openAddModal(): void {
    this.formMode = 'add';
    this.currentFormDepartment = {
      name: '',
      managerId: null,
      location: '',
      budget: undefined,
      description: ''
    };
    this.currentManagerNonRole = null;
    this.submitted = false;
    this.touchedFields = {};
    this.validateForm();
    this.showFormModal = true;
  }

  openEditModal(dept: Department): void {
    this.formMode = 'edit';
    const mgrId = dept.manager ? dept.manager.id : (dept.managerId !== undefined ? dept.managerId : null);

    this.currentFormDepartment = {
      ...dept,
      managerId: mgrId
    };

    // Vérifie si le manager sélectionné a bien le rôle MANAGER
    if (mgrId) {
      const inAvailable = this.availableManagers.some(m => m.id === mgrId);
      if (!inAvailable) {
        const foundInAll = this.allEmployees.find(e => e.id === mgrId);
        if (foundInAll) {
          this.currentManagerNonRole = foundInAll;
        } else if (dept.manager) {
          this.currentManagerNonRole = {
            id: dept.manager.id,
            firstName: dept.manager.firstName,
            lastName: dept.manager.lastName,
            name: `${dept.manager.firstName} ${dept.manager.lastName}`,
            email: '', phone: '', position: 'Ancien Manager', department: dept.name, hireDate: '', status: 'ACTIVE', salary: 0, photo: ''
          };
        }
      } else {
        this.currentManagerNonRole = null;
      }
    } else {
      this.currentManagerNonRole = null;
    }

    this.submitted = false;
    this.touchedFields = {};
    this.validateForm();
    this.showFormModal = true;
  }

  closeFormModal(): void {
    this.showFormModal = false;
    this.currentFormDepartment = {};
    this.formErrors = {};
    this.touchedFields = {};
    this.submitted = false;
    this.currentManagerNonRole = null;
  }

  markTouched(field: keyof DepartmentFormErrors): void {
    this.touchedFields[field] = true;
    this.validateForm();
  }

  onFieldChange(field: keyof DepartmentFormErrors): void {
    this.validateForm();
  }

  validateForm(): boolean {
    const errors: DepartmentFormErrors = {};

    // 1. Nom : obligatoire, min 2 chars, uniquement lettres/chiffres/espaces/tirets
    const name = this.currentFormDepartment.name ? this.currentFormDepartment.name.trim() : '';
    if (!name) {
      errors.name = 'Le nom du département est obligatoire.';
    } else if (name.length < 2) {
      errors.name = 'Le nom du département doit comporter au moins 2 caractères.';
    } else if (!/^[a-zA-Z0-9À-ÿ\s-]+$/.test(name)) {
      errors.name = 'Le nom ne doit contenir que des lettres, des chiffres, des espaces et des tirets (pas de caractères spéciaux).';
    }

    // 2. Localisation : obligatoire, une des valeurs de governorates
    const location = this.currentFormDepartment.location ? this.currentFormDepartment.location.trim() : '';
    if (!location) {
      errors.location = 'La localisation est obligatoire.';
    } else {
      const match = this.governorates.find(g => g.toLowerCase() === location.toLowerCase());
      if (!match) {
        errors.location = 'La localisation doit correspondre à un gouvernorat valide.';
      }
    }

    // 3. Manager : optionnel si "Non assigné"
    const mgrId = this.currentFormDepartment.managerId;
    if (mgrId !== null && mgrId !== undefined && (mgrId as any) !== '') {
      const numId = Number(mgrId);
      const isValidMgr = this.availableManagers.some(m => m.id === numId) || 
                         (this.currentManagerNonRole && this.currentManagerNonRole.id === numId);
      if (!isValidMgr) {
        errors.managerId = 'Le manager sélectionné est invalide.';
      }
    }

    // 4. Budget : obligatoire, nombre, strictement positif (> 0)
    const budget = this.currentFormDepartment.budget;
    if (budget === null || budget === undefined || (typeof budget === 'string' && (budget as string).trim() === '')) {
      errors.budget = 'Le budget annuel est obligatoire.';
    } else {
      const numBudget = Number(budget);
      if (isNaN(numBudget)) {
        errors.budget = 'Le budget doit être un nombre valide.';
      } else if (numBudget <= 0) {
        errors.budget = 'Le budget doit être un nombre strictement positif (> 0).';
      }
    }

    // 5. Description : optionnel, max 500 caractères
    const desc = this.currentFormDepartment.description || '';
    if (desc.length > 500) {
      errors.description = 'La description ne doit pas dépasser 500 caractères.';
    }

    this.formErrors = errors;
    return Object.keys(errors).length === 0;
  }

  get isFormValid(): boolean {
    return Object.keys(this.formErrors).length === 0;
  }

  saveDepartment(): void {
    this.submitted = true;

    if (!this.validateForm()) {
      const firstError = Object.values(this.formErrors)[0];
      this.toastService.error(firstError || 'Veuillez corriger les erreurs du formulaire.');
      return;
    }

    const requestData: DepartmentRequest = {
      name: this.currentFormDepartment.name!.trim(),
      description: this.currentFormDepartment.description ? this.currentFormDepartment.description.trim() : '',
      managerId: (this.currentFormDepartment.managerId !== null && this.currentFormDepartment.managerId !== undefined && (this.currentFormDepartment.managerId as any) !== '') 
        ? Number(this.currentFormDepartment.managerId) 
        : null,
      location: this.currentFormDepartment.location!.trim(),
      budget: Number(this.currentFormDepartment.budget)
    };

    if (this.formMode === 'add') {
      this.departmentService.create(requestData).subscribe({
        next: () => {
          this.toastService.success('Département créé avec succès !');
          this.closeFormModal();
          this.loadDepartments();
        },
        error: (err) => {
          console.error('Error creating department', err);
          this.error = 'Erreur lors de la création du département.';
          this.toastService.error('Erreur lors de la création du département.');
        }
      });
    } else {
      const id = this.currentFormDepartment.id;
      if (id) {
        this.departmentService.update(id, requestData).subscribe({
          next: () => {
            this.toastService.success('Département mis à jour avec succès !');
            this.closeFormModal();
            this.loadDepartments();
          },
          error: (err) => {
            console.error('Error updating department', err);
            this.error = 'Erreur lors de la modification du département.';
            this.toastService.error('Erreur lors de la modification du département.');
          }
        });
      }
    }
  }

  async deleteDepartment(id: number): Promise<void> {
    const confirmed = await this.confirmService.showConfirm({
      title: 'Suppression de département',
      message: 'Êtes-vous sûr de vouloir supprimer ce département ? Cette action est irréversible.',
      confirmText: 'Supprimer',
      cancelText: 'Annuler',
      type: 'danger'
    });

    if (confirmed) {
      this.departmentService.delete(id).subscribe({
        next: () => {
          this.toastService.success('Département supprimé avec succès !');
          this.loadDepartments();
          if (this.selectedDepartment && this.selectedDepartment.id === id) {
            this.closeDetailPanel();
          }
        },
        error: (err) => {
          console.error('Error deleting department', err);
          this.error = 'Erreur lors de la suppression du département.';
          this.toastService.error('Erreur lors de la suppression du département.');
        }
      });
    }
  }
}
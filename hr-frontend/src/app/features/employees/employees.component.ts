import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { EmployeeService } from '../../core/services/employee.service';
import { Employee, EmployeeRequest } from '../../core/models/employee.model';
import { DepartmentService } from '../../core/services/department.service';
import { Department } from '../../core/models/interfaces';
import { ConfirmDialogService } from '../../core/services/confirm-dialog.service';
import { ToastNotificationService } from '../../core/services/toast-notification.service';

export function tunisianPhoneValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value;
    if (!value) return null;

    let digits = String(value).trim().replace(/^\+216/, '').replace(/\s+/g, '');

    if (!/^\d+$/.test(digits)) {
      return { nonNumeric: true };
    }

    if (digits.length !== 8) {
      return { invalidLength: true };
    }

    const validPattern = /^(2[0-9]|9[2-9]|5[0-9])\d{6}$/;
    if (!validPattern.test(digits)) {
      return { invalidPrefix: true };
    }

    return null;
  };
}

export function pastOrPresentValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    if (!control.value) return null;
    const inputDate = new Date(control.value);
    const today = new Date();
    today.setHours(23, 59, 59, 999);
    if (inputDate > today) {
      return { futureDate: true };
    }
    return null;
  };
}

@Component({
  selector: 'app-employees',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './employees.component.html',
  styleUrl: './employees.component.scss'
})
export class EmployeesComponent implements OnInit {
  employees: Employee[] = [];
  filteredEmployees: Employee[] = [];
  
  searchText = '';
  selectedDepartment = '';
  selectedStatus = '';

  // Reactive Form
  employeeForm!: FormGroup;

  // Form Modal state
  showFormModal = false;
  formMode: 'add' | 'edit' = 'add';
  currentFormEmployee: Partial<Employee> = {};

  // Details panel state
  showDetailPanel = false;
  selectedEmployee: Employee | null = null;

  // Photo Upload state
  selectedFile: File | null = null;
  photoPreviewUrl: string | null = null;
  isUploading = false;
  uploadError: string | null = null;

  // Department List
  departmentsList: Department[] = [];

  // Managers list (for dropdown)
  managersList: Employee[] = [];

  constructor(
    private employeeService: EmployeeService,
    private departmentService: DepartmentService,
    private fb: FormBuilder,
    private confirmService: ConfirmDialogService,
    private toastService: ToastNotificationService
  ) {
    this.initForm();
  }

  ngOnInit(): void {
    this.loadEmployees();
    this.loadDepartments();
    this.loadManagers();
  }

  initForm(): void {
    this.employeeForm = this.fb.group({
      firstName: ['', [Validators.required, Validators.pattern('^[A-ZÀ-ÖØ-Þ][a-zà-öø-ÿA-ZÀ-ÖØ-öø-ÿ\\s-]*$')]],
      lastName: ['', [Validators.required, Validators.pattern('^[A-ZÀ-ÖØ-Þ][a-zà-öø-ÿA-ZÀ-ÖØ-öø-ÿ\\s-]*$')]],
      email: ['', [Validators.required, Validators.email]],
      phone: ['', [Validators.required, tunisianPhoneValidator()]],
      department: ['IT', [Validators.required]],
      position: ['', [Validators.required, Validators.pattern('^[A-Za-zÀ-ÖØ-öø-ÿ\\s-]+$')]],
      hireDate: [new Date().toISOString().split('T')[0], [Validators.required, pastOrPresentValidator()]],
      status: ['ACTIVE', [Validators.required]],
      salary: [45000, [Validators.required, Validators.min(0.001)]],
      managerId: [null]
    });
  }

  loadManagers(): void {
    this.employeeService.getManagers().subscribe({
      next: (data) => { this.managersList = data; },
      error: (err) => console.warn('Could not load managers list', err)
    });
  }

  loadDepartments(): void {
    this.departmentService.getAll().subscribe({
      next: (data) => {
        this.departmentsList = data;
      },
      error: (err) => {
        console.error('Error loading departments in EmployeesComponent', err);
      }
    });
  }

  loadEmployees(): void {
    this.employeeService.getEmployees(0, 100, this.searchText).subscribe({
      next: (data) => {
        this.employees = data.content || [];
        this.applyFilters();
      },
      error: (err) => {
        console.error('Error loading employees', err);
      }
    });
  }

  applyFilters() {
    this.filteredEmployees = this.employees.filter(emp => {
      const matchesSearch = !this.searchText ||
        (emp.firstName && emp.firstName.toLowerCase().includes(this.searchText.toLowerCase())) ||
        (emp.lastName && emp.lastName.toLowerCase().includes(this.searchText.toLowerCase())) ||
        (emp.email && emp.email.toLowerCase().includes(this.searchText.toLowerCase())) ||
        (emp.position && emp.position.toLowerCase().includes(this.searchText.toLowerCase()));
      
      const matchesDept = !this.selectedDepartment || emp.department === this.selectedDepartment;
      const matchesStatus = !this.selectedStatus || emp.status === this.selectedStatus;

      return matchesSearch && matchesDept && matchesStatus;
    });
  }

  onSearchChange() {
    this.loadEmployees();
  }

  viewEmployee(emp: Employee) {
    this.selectedEmployee = emp;
    this.showDetailPanel = true;
  }

  closeDetailPanel() {
    this.showDetailPanel = false;
    this.selectedEmployee = null;
  }

  openAddModal() {
    this.formMode = 'add';
    this.currentFormEmployee = {};
    const defaultDept = this.departmentsList.length > 0 ? this.departmentsList[0].name : 'IT';
    
    this.employeeForm.reset({
      firstName: '',
      lastName: '',
      email: '',
      phone: '',
      department: defaultDept,
      position: '',
      hireDate: new Date().toISOString().split('T')[0],
      status: 'ACTIVE',
      salary: 45000,
      managerId: null
    });

    this.selectedFile = null;
    this.photoPreviewUrl = null;
    this.uploadError = null;
    this.isUploading = false;
    this.showFormModal = true;
  }

  openEditModal(emp: Employee) {
    this.formMode = 'edit';
    this.currentFormEmployee = { ...emp };

    let rawPhone = emp.phone || '';
    if (rawPhone.startsWith('+216')) {
      rawPhone = rawPhone.substring(4);
    }

    this.employeeForm.reset({
      firstName: emp.firstName || '',
      lastName: emp.lastName || '',
      email: emp.email || '',
      phone: rawPhone,
      department: emp.department || (this.departmentsList.length > 0 ? this.departmentsList[0].name : 'IT'),
      position: emp.position || '',
      hireDate: emp.hireDate || new Date().toISOString().split('T')[0],
      status: emp.status || 'ACTIVE',
      salary: emp.salary || 45000,
      managerId: emp.managerId ?? null
    });

    this.selectedFile = null;
    this.photoPreviewUrl = null;
    this.uploadError = null;
    this.isUploading = false;
    this.showFormModal = true;
  }

  closeFormModal() {
    this.showFormModal = false;
    this.currentFormEmployee = {};
    this.selectedFile = null;
    this.photoPreviewUrl = null;
    this.uploadError = null;
  }

  onFileSelected(event: any): void {
    const file = event.target.files?.[0];
    if (!file) return;

    this.uploadError = null;

    if (!file.type.startsWith('image/')) {
      this.uploadError = 'Le fichier doit être une image.';
      return;
    }

    const maxSizeBytes = 2 * 1024 * 1024;
    if (file.size > maxSizeBytes) {
      this.uploadError = 'La taille de l\'image ne doit pas dépasser 2 Mo.';
      return;
    }

    this.selectedFile = file;

    const reader = new FileReader();
    reader.onload = () => {
      this.photoPreviewUrl = reader.result as string;
    };
    reader.readAsDataURL(file);
  }

  // Keypress event handlers to enforce input restrictions in real-time
  onNameKeyPress(event: KeyboardEvent): void {
    const char = event.key;
    if (/^[A-ZÀ-ÖØ-Þa-zà-öø-ÿ\s-]$/.test(char) || event.ctrlKey || event.altKey) {
      return;
    }
    event.preventDefault();
  }

  onPhoneKeyPress(event: KeyboardEvent): void {
    const char = event.key;
    if (/^[0-9]$/.test(char) || event.ctrlKey || event.altKey) {
      return;
    }
    event.preventDefault();
  }

  onSalaryKeyPress(event: KeyboardEvent): void {
    const char = event.key;
    if (/^[0-9.,]$/.test(char) || event.ctrlKey || event.altKey) {
      return;
    }
    event.preventDefault();
  }

  getTunisianOperator(phone: string | null | undefined): { name: string; class: string; icon: string } | null {
    if (!phone) return null;
    let digits = String(phone).trim().replace(/^\+216/, '').replace(/\s+/g, '');
    if (digits.length < 2) return null;

    const prefix2 = digits.substring(0, 2);
    const num2 = parseInt(prefix2, 10);

    if (num2 >= 20 && num2 <= 29) {
      return { name: 'Ooredoo Tunisie', class: 'operator-ooredoo', icon: 'signal_cellular_alt' };
    }
    if (num2 >= 92 && num2 <= 99) {
      return { name: 'Tunisie Telecom', class: 'operator-tt', icon: 'cell_tower' };
    }
    if (num2 >= 50 && num2 <= 59) {
      return { name: 'Orange Tunisie', class: 'operator-orange', icon: 'rss_feed' };
    }
    return null;
  }

  saveEmployee() {
    if (this.employeeForm.invalid) {
      this.employeeForm.markAllAsTouched();
      this.uploadError = 'Veuillez corriger les erreurs dans le formulaire.';
      this.toastService.error('Veuillez corriger les erreurs dans le formulaire.');
      return;
    }

    const formVal = this.employeeForm.value;
    const rawPhone = String(formVal.phone || '').trim().replace(/^\+216/, '');

    const requestData: EmployeeRequest = {
      firstName: formVal.firstName.trim(),
      lastName: formVal.lastName.trim(),
      email: formVal.email.trim(),
      phone: '+216' + rawPhone,
      position: formVal.position.trim(),
      department: formVal.department,
      hireDate: formVal.hireDate,
      salary: Number(formVal.salary),
      status: formVal.status,
      managerId: formVal.managerId ?? null
    };

    if (this.formMode === 'add') {
      this.employeeService.createEmployee(requestData).subscribe({
        next: (createdEmp) => {
          this.toastService.success('Employé créé avec succès !');
          this.handlePhotoUpload(createdEmp.id);
        },
        error: (err) => {
          console.error('Error creating employee', err);
          let errMsg = err?.error?.message || 'Erreur lors de la création de l\'employé.';
          if (err?.error?.errors) {
            errMsg = 'Erreur backend : ' + Object.values(err.error.errors).join(' | ');
          }
          this.uploadError = errMsg;
          this.toastService.error(errMsg);
        }
      });
    } else {
      const id = this.currentFormEmployee.id!;
      this.employeeService.updateEmployee(id, requestData).subscribe({
        next: (updatedEmp) => {
          this.toastService.success('Employé mis à jour avec succès !');
          this.handlePhotoUpload(id);
        },
        error: (err) => {
          console.error('Error updating employee', err);
          let errMsg = err?.error?.message || 'Erreur lors de la modification de l\'employé.';
          if (err?.error?.errors) {
            errMsg = 'Erreur backend : ' + Object.values(err.error.errors).join(' | ');
          }
          this.uploadError = errMsg;
          this.toastService.error(errMsg);
        }
      });
    }
  }

  handlePhotoUpload(employeeId: number) {
    if (this.selectedFile) {
      this.isUploading = true;
      this.employeeService.uploadPhoto(employeeId, this.selectedFile).subscribe({
        next: () => {
          this.isUploading = false;
          this.toastService.success('Photo de profil enregistrée avec succès.');
          this.closeFormModal();
          this.loadEmployees();
        },
        error: (err) => {
          this.isUploading = false;
          console.error('Error uploading photo', err);
          this.uploadError = 'Erreur lors du transfert de la photo.';
          this.toastService.error('Erreur lors du transfert de la photo.');
        }
      });
    } else {
      this.closeFormModal();
      this.loadEmployees();
    }
  }

  async deleteEmployee(id: number) {
    const confirmed = await this.confirmService.showConfirm({
      title: 'Suppression d\'un employé',
      message: 'Êtes-vous sûr de vouloir supprimer cet employé ? Cette action est irréversible.',
      confirmText: 'Supprimer',
      cancelText: 'Annuler',
      type: 'danger'
    });

    if (confirmed) {
      this.employeeService.deleteEmployee(id).subscribe({
        next: () => {
          this.toastService.success('Employé supprimé avec succès.');
          this.loadEmployees();
        },
        error: (err) => {
          console.error('Error deleting employee', err);
          this.toastService.error('Erreur lors de la suppression de l\'employé.');
        }
      });
    }
  }

  getEmployeePhoto(emp: any): string {
    if (emp && emp.id && emp.photoUrl) {
      return this.employeeService.getPhotoUrl(emp.id);
    }
    return 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150';
  }

  // Helper getters for template validation feedback
  get f() {
    return this.employeeForm.controls;
  }
}

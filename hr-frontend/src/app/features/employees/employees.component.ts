import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EmployeeService } from '../../core/services/employee.service';
import { Employee, EmployeeRequest } from '../../core/models/employee.model';

@Component({
  selector: 'app-employees',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './employees.component.html',
  styleUrl: './employees.component.scss'
})
export class EmployeesComponent implements OnInit {
  employees: Employee[] = [];
  filteredEmployees: Employee[] = [];
  
  searchText = '';
  selectedDepartment = '';
  selectedStatus = '';

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

  constructor(private employeeService: EmployeeService) {}

  ngOnInit(): void {
    this.loadEmployees();
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
    this.currentFormEmployee = {
      firstName: '',
      lastName: '',
      email: '',
      phone: '',
      department: 'IT',
      position: '',
      status: 'ACTIVE',
      salary: 45000,
      hireDate: new Date().toISOString().split('T')[0]
    };
    this.selectedFile = null;
    this.photoPreviewUrl = null;
    this.uploadError = null;
    this.isUploading = false;
    this.showFormModal = true;
  }

  openEditModal(emp: Employee) {
    this.formMode = 'edit';
    this.currentFormEmployee = { 
      ...emp,
      salary: emp.salary || 45000
    };
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

  saveEmployee() {
    const requestData: EmployeeRequest = {
      firstName: this.currentFormEmployee.firstName || '',
      lastName: this.currentFormEmployee.lastName || '',
      email: this.currentFormEmployee.email || '',
      phone: this.currentFormEmployee.phone || '',
      position: this.currentFormEmployee.position || '',
      department: this.currentFormEmployee.department || 'IT',
      hireDate: this.currentFormEmployee.hireDate || new Date().toISOString().split('T')[0],
      salary: this.currentFormEmployee.salary || 45000
    };

    if (!requestData.firstName.trim() || !requestData.lastName.trim() || !requestData.email.trim()) {
      this.uploadError = 'Veuillez remplir tous les champs obligatoires (Prénom, Nom, Email).';
      return;
    }

    if (this.formMode === 'add') {
      this.employeeService.createEmployee(requestData).subscribe({
        next: (createdEmp) => {
          this.handlePhotoUpload(createdEmp.id);
        },
        error: (err) => {
          console.error('Error creating employee', err);
          this.uploadError = 'Erreur lors de la création de l\'employé.';
        }
      });
    } else {
      const id = this.currentFormEmployee.id!;
      this.employeeService.updateEmployee(id, requestData).subscribe({
        next: (updatedEmp) => {
          this.handlePhotoUpload(id);
        },
        error: (err) => {
          console.error('Error updating employee', err);
          this.uploadError = 'Erreur lors de la modification de l\'employé.';
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
          this.closeFormModal();
          this.loadEmployees();
        },
        error: (err) => {
          this.isUploading = false;
          console.error('Error uploading photo', err);
          this.uploadError = 'Erreur lors du transfert de la photo.';
        }
      });
    } else {
      this.closeFormModal();
      this.loadEmployees();
    }
  }

  deleteEmployee(id: number) {
    if (confirm('Êtes-vous sûr de vouloir supprimer cet employé ?')) {
      this.employeeService.deleteEmployee(id).subscribe({
        next: () => {
          this.loadEmployees();
        },
        error: (err) => {
          console.error('Error deleting employee', err);
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
}


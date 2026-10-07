import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { DepartmentService } from '../../core/services/department.service';
import { EmployeeService } from '../../core/services/employee.service';
import { Employee } from '../../core/models/interfaces';

@Component({
    selector: 'app-department-form',
    standalone: true,
    imports: [CommonModule, ReactiveFormsModule],
    templateUrl: './department-form.component.html',
    styleUrls: ['./department-form.component.scss']
})
export class DepartmentFormComponent implements OnInit {
    form: FormGroup;
    isEditMode = false;
    departmentId: number | null = null;
    employees: Employee[] = [];
    governorates: string[] = [
        'Ariana', 'Béja', 'Ben Arous', 'Bizerte', 'Gabès', 'Gafsa', 
        'Jendouba', 'Kairouan', 'Kasserine', 'Kébili', 'Kef', 'Mahdia', 
        'Manouba', 'Médenine', 'Monastir', 'Nabeul', 'Sfax', 'Sidi Bouzid', 
        'Siliana', 'Sousse', 'Tataouine', 'Tozeur', 'Tunis', 'Zaghouan'
    ];
    loading = false;
    saving = false;
    error = '';

    constructor(
        private fb: FormBuilder,
        private departmentService: DepartmentService,
        private employeeService: EmployeeService,
        private route: ActivatedRoute,
        private router: Router
    ) {
        this.form = this.fb.group({
            name: ['', Validators.required],
            description: [''],
            managerId: [null],
            location: [''],
            budget: [0]
        });
    }

    ngOnInit(): void {
        this.loading = true;

        // Charge la liste des employés pour le select "chef de département"
        this.employeeService.getEmployees(0, 100, '').subscribe({
            next: (res: any) => {
                this.employees = res.content || res;
            },
            error: () => this.employees = []
        });

        const idParam = this.route.snapshot.paramMap.get('id');
        if (idParam) {
            this.isEditMode = true;
            this.departmentId = +idParam;

            this.departmentService.getById(this.departmentId).subscribe({
                next: (dept) => {
                    this.form.patchValue({
                        name: dept.name,
                        description: dept.description,
                        managerId: dept.manager?.id || null,
                        location: dept.location || '',
                        budget: dept.budget || 0
                    });
                    this.loading = false;
                },
                error: () => {
                    this.error = 'Département introuvable';
                    this.loading = false;
                }
            });
        } else {
            this.loading = false;
        }
    }

    onSubmit(): void {
        if (this.form.invalid) {
            this.form.markAllAsTouched();
            return;
        }

        this.saving = true;
        this.error = '';
        const dto = this.form.value;

        const request$ = this.isEditMode && this.departmentId
            ? this.departmentService.update(this.departmentId, dto)
            : this.departmentService.create(dto);

        request$.subscribe({
            next: () => this.router.navigate(['/departments']),
            error: (err) => {
                this.error = err.error?.error || 'Une erreur est survenue';
                this.saving = false;
            }
        });
    }

    onCancel(): void {
        this.router.navigate(['/departments']);
    }
}
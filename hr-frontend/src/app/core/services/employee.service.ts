import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { Employee, EmployeeRequest } from '../models/employee.model';

@Injectable({
  providedIn: 'root'
})
export class EmployeeService {
  private apiUrl = 'http://localhost:8087/api/employees';

  private employeesSubject = new BehaviorSubject<Employee[]>([]);
  public employees$ = this.employeesSubject.asObservable();

  private departmentsSubject = new BehaviorSubject<any[]>([]);
  public departments$ = this.departmentsSubject.asObservable();

  private defaultDepartments: any[] = [
    {
      name: 'IT',
      employeesCount: 25,
      manager: 'Thomas Rousseau',
      budget: '750,000 €',
      description: 'Développement logiciel, infrastructures réseaux, support informatique et projets IA.'
    },
    {
      name: 'RH',
      employeesCount: 10,
      manager: 'Marc Dubois',
      budget: '180,000 €',
      description: 'Gestion des talents, paie, recrutement, formations et bien-être des collaborateurs.'
    },
    {
      name: 'Marketing',
      employeesCount: 18,
      manager: 'Camille Laurent',
      budget: '320,000 €',
      description: 'Communication externe, image de marque, réseaux sociaux et stratégies de croissance.'
    },
    {
      name: 'Finance',
      employeesCount: 12,
      manager: 'Youssef Kabbaj',
      budget: '450,000 €',
      description: 'Comptabilité générale, facturation, contrôle budgétaire et planification financière.'
    }
  ];

  constructor(private http: HttpClient) {
    const savedDeps = localStorage.getItem('hr_departments');
    if (savedDeps) {
      this.departmentsSubject.next(JSON.parse(savedDeps));
    } else {
      localStorage.setItem('hr_departments', JSON.stringify(this.defaultDepartments));
      this.departmentsSubject.next(this.defaultDepartments);
    }

    // Automatically load employees on startup to populate BehaviorSubject
    this.loadInitialEmployees();
  }

  private loadInitialEmployees() {
    this.getEmployees(0, 100, '').subscribe({
      next: () => { },
      error: (err) => {
        console.error('Failed to load initial employees in EmployeeService constructor:', err);
      }
    });
  }

  getEmployees(): Employee[];
  getEmployees(page: number, size: number, search: string): Observable<any>;
  getEmployees(page?: number, size?: number, search?: string): any {
    if (page === undefined) {
      return this.employeesSubject.value;
    }

    let params = new HttpParams();
    if (page !== undefined && page !== null) {
      params = params.set('page', page.toString());
    }
    if (size !== undefined && size !== null) {
      params = params.set('size', size.toString());
    }
    if (search) {
      params = params.set('search', search);
    }

    return this.http.get<any>(this.apiUrl, { params }).pipe(
      tap(res => {
        if (res && res.content) {
          const mapped = res.content.map((emp: any) => ({
            ...emp,
            name: emp.lastName, // legacy support for evaluation page and other components
            photo: emp.photoUrl ? this.getPhotoUrl(emp.id) : 'https://media..com/id//fr/vectoriel/ic%C3%B4ne-de-profil-utilisateur-avatar-ou-ic%C3%B4ne-de-personne-photo-de-profil-symbole-portrait.jpg?s=2048x2048&w=is&k=20&c=m5Xw61esitYlBSQxzxT0hbHtFjVCl526qoLTX1vbvwk=', // legacy support
            salary: emp.salary || 45000 // legacy support
          }));
          this.employeesSubject.next(mapped);
        }
      })
    );
  }

  getEmployeeById(id: number): Observable<Employee> {
    return this.http.get<Employee>(`${`${this.apiUrl}/${id}`}`);
  }

  createEmployee(employee: EmployeeRequest): Observable<Employee> {
    return this.http.post<Employee>(this.apiUrl, employee);
  }

  updateEmployee(id: number, employee: EmployeeRequest): Observable<Employee> {
    return this.http.put<Employee>(`${`${this.apiUrl}/${id}`}`, employee);
  }

  deleteEmployee(id: number): Observable<void> {
    return this.http.delete<void>(`${`${this.apiUrl}/${id}`}`);
  }

  uploadPhoto(id: number, file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<any>(`${`${this.apiUrl}/${id}/photo`}`, formData);
  }

  getPhotoUrl(id: number): string {
    return `${`${this.apiUrl}/${id}/photo`}`;
  }
}

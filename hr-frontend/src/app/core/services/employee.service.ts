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
      budget: '750,000 TND',
      description:
        'Développement logiciel, infrastructures réseaux, support informatique et projets IA.'
    },
    {
      name: 'RH',
      employeesCount: 10,
      manager: 'Marc Dubois',
      budget: '180,000 TND',
      description:
        'Gestion des talents, paie, recrutement, formations et bien-être des collaborateurs.'
    },
    {
      name: 'Marketing',
      employeesCount: 18,
      manager: 'Camille Laurent',
      budget: '320,000 TND',
      description:
        'Communication externe, image de marque, réseaux sociaux et stratégies de croissance.'
    },
    {
      name: 'Finance',
      employeesCount: 12,
      manager: 'Youssef Kabbaj',
      budget: '450,000 TND',
      description:
        'Comptabilité générale, facturation, contrôle budgétaire et planification financière.'
    }
  ];

  constructor(private http: HttpClient) {

    const savedDeps =
      localStorage.getItem('hr_departments');

    if (savedDeps) {

      try {
        this.departmentsSubject.next(
          JSON.parse(savedDeps)
        );
      } catch (error) {

        console.error(
          'Erreur lors du chargement des départements depuis localStorage',
          error
        );

        this.departmentsSubject.next(
          this.defaultDepartments
        );
      }

    } else {

      localStorage.setItem(
        'hr_departments',
        JSON.stringify(this.defaultDepartments)
      );

      this.departmentsSubject.next(
        this.defaultDepartments
      );
    }

    /*
     * IMPORTANT :
     * Ne pas charger automatiquement tous les employés ici.
     *
     * Avant :
     * this.loadInitialEmployees();
     *
     * Cela provoquait :
     * GET /api/employees?page=0&size=100
     *
     * et donc 403 pour le rôle EMPLOYEE.
     */
  }


  // =========================================================
  // GET CURRENT EMPLOYEE
  // =========================================================

  getCurrentEmployee(): Observable<Employee> {
    return this.http.get<Employee>(
      `${this.apiUrl}/me`
    );
  }

  updateMyProfile(employee: EmployeeRequest): Observable<Employee> {
    return this.http.put<Employee>(
      `${this.apiUrl}/me`,
      employee
    );
  }


  // =========================================================
  // GET ALL EMPLOYEES
  // ADMIN / RH / MANAGER
  // =========================================================

  getEmployees(): Employee[];
  getEmployees(
    page: number,
    size: number,
    search: string
  ): Observable<any>;

  getEmployees(
    page?: number,
    size?: number,
    search?: string
  ): Observable<any> | Employee[] {

    /*
     * Sans paramètres :
     * retourne les employés déjà présents
     * dans le BehaviorSubject.
     */
    if (
      page === undefined &&
      size === undefined &&
      search === undefined
    ) {
      return this.employeesSubject.value;
    }

    let params = new HttpParams();

    if (page !== undefined && page !== null) {
      params = params.set(
        'page',
        page.toString()
      );
    }

    if (size !== undefined && size !== null) {
      params = params.set(
        'size',
        size.toString()
      );
    }

    if (search && search.trim().length > 0) {
      params = params.set(
        'search',
        search.trim()
      );
    }

    return this.http
      .get<any>(this.apiUrl, { params })
      .pipe(

        tap(res => {

          if (
            res &&
            Array.isArray(res.content)
          ) {

            const mapped =
              res.content.map(
                (emp: any) => ({

                  ...emp,

                  // Support legacy
                  name:
                    emp.lastName,

                  // Support legacy
                  photo:
                    emp.photoUrl
                      ? this.getPhotoUrl(emp.id)
                      : 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150',

                  // Support legacy
                  salary:
                    emp.salary || 45000
                })
              );

            this.employeesSubject.next(
              mapped
            );
          }
        })
      );
  }


  // =========================================================
  // GET EMPLOYEE BY ID
  // =========================================================

  getEmployeeById(
    id: number
  ): Observable<Employee> {

    return this.http.get<Employee>(
      `${this.apiUrl}/${id}`
    );
  }


  // =========================================================
  // CREATE EMPLOYEE
  // =========================================================

  createEmployee(
    employee: EmployeeRequest
  ): Observable<Employee> {

    return this.http.post<Employee>(
      this.apiUrl,
      employee
    );
  }


  // =========================================================
  // UPDATE EMPLOYEE
  // =========================================================

  updateEmployee(
    id: number,
    employee: EmployeeRequest
  ): Observable<Employee> {

    return this.http.put<Employee>(
      `${this.apiUrl}/${id}`,
      employee
    );
  }


  // =========================================================
  // DELETE EMPLOYEE
  // =========================================================

  deleteEmployee(
    id: number
  ): Observable<void> {

    return this.http.delete<void>(
      `${this.apiUrl}/${id}`
    );
  }


  // =========================================================
  // UPLOAD PHOTO
  // =========================================================

  uploadPhoto(
    id: number,
    file: File
  ): Observable<any> {

    const formData = new FormData();

    formData.append(
      'file',
      file
    );

    return this.http.post<any>(
      `${this.apiUrl}/${id}/photo`,
      formData
    );
  }

  uploadMyPhoto(
    file: File
  ): Observable<any> {

    const formData = new FormData();

    formData.append(
      'file',
      file
    );

    return this.http.post<any>(
      `${this.apiUrl}/me/photo`,
      formData
    );
  }


  // =========================================================
  // PHOTO URL
  // =========================================================

  getPhotoUrl(
    id: number
  ): string {

    return `${this.apiUrl}/${id}/photo`;
  }


  // =========================================================
  // LISTE DES MANAGERS (dropdown)
  // =========================================================

  /**
   * Retourne tous les employés pouvant être désignés managers
   * GET /api/employees/managers
   */
  getManagers(): Observable<Employee[]> {
    return this.http.get<Employee[]>(`${this.apiUrl}/managers`);
  }


  // =========================================================
  // AFFECTER UN MANAGER À UN EMPLOYÉ
  // =========================================================

  /**
   * Affecte ou change le manager d'un employé.
   * managerId = null → désaffectation
   * PUT /api/employees/{id}/manager
   */
  assignManager(employeeId: number, managerId: number | null): Observable<Employee> {
    return this.http.put<Employee>(
      `${this.apiUrl}/${employeeId}/manager`,
      { managerId }
    );
  }


  // =========================================================
  // ÉQUIPE D'UN MANAGER (vue Admin/RH)
  // =========================================================

  /**
   * Liste des employés rattachés à un manager.
   * GET /api/employees/by-manager/{managerId}/team
   */
  getManagerTeam(managerId: number): Observable<Employee[]> {
    return this.http.get<Employee[]>(
      `${this.apiUrl}/by-manager/${managerId}/team`
    );
  }
}
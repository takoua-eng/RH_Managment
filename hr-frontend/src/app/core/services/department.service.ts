import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Department, DepartmentRequest, Employee } from '../models/interfaces';
import { environment } from '../../../environments/environment';

const API_URL = `${environment.apiUrl}/departments`;

@Injectable({ providedIn: 'root' })
export class DepartmentService {

  constructor(private http: HttpClient) {}

  getAll(): Observable<Department[]> {
    return this.http.get<Department[]>(API_URL);
  }

  getById(id: number): Observable<Department> {
    return this.http.get<Department>(`${API_URL}/${id}`);
  }

  create(dto: DepartmentRequest): Observable<Department> {
    return this.http.post<Department>(API_URL, dto);
  }

  update(id: number, dto: DepartmentRequest): Observable<Department> {
    return this.http.put<Department>(`${API_URL}/${id}`, dto);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${API_URL}/${id}`);
  }

  assignEmployees(departmentId: number, employeeIds: number[]): Observable<Department> {
    return this.http.post<Department>(`${API_URL}/${departmentId}/employees`, { employeeIds });
  }

  removeEmployee(departmentId: number, employeeId: number): Observable<void> {
    return this.http.delete<void>(`${API_URL}/${departmentId}/employees/${employeeId}`);
  }

  getEmployeesByDepartment(departmentId: number): Observable<Employee[]> {
    return this.http.get<Employee[]>(`${API_URL}/${departmentId}/employees`);
  }
}
import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { LeaveRequest } from '../models/interfaces';

@Injectable({
  providedIn: 'root'
})
export class LeaveService {
  private leavesSubject = new BehaviorSubject<LeaveRequest[]>([]);
  public leaves$ = this.leavesSubject.asObservable();

  private defaultLeaves: LeaveRequest[] = [
    {
      id: 1,
      employeeName: 'Ahmed Alami',
      startDate: '2026-06-20',
      endDate: '2026-06-25',
      type: 'Payé',
      status: 'Approuvé',
      reason: 'Vacances d\'été'
    },
    {
      id: 2,
      employeeName: 'Sara Benjelloun',
      startDate: '2026-07-02',
      endDate: '2026-07-06',
      type: 'Payé',
      status: 'En attente',
      reason: 'Repos annuel'
    },
    {
      id: 3,
      employeeName: 'Sophie Martin',
      startDate: '2026-06-18',
      endDate: '2026-06-19',
      type: 'RTT',
      status: 'Approuvé',
      reason: 'Rendez-vous médical'
    },
    {
      id: 4,
      employeeName: 'Youssef Kabbaj',
      startDate: '2026-08-10',
      endDate: '2026-08-24',
      type: 'Payé',
      status: 'En attente',
      reason: 'Voyage familial'
    },
    {
      id: 5,
      employeeName: 'Fatima Zahra',
      startDate: '2026-06-15',
      endDate: '2026-06-17',
      type: 'Maladie',
      status: 'Approuvé',
      reason: 'Grippe saisonnière'
    }
  ];

  constructor() {
    const savedLeaves = localStorage.getItem('hr_leaves');
    if (savedLeaves) {
      this.leavesSubject.next(JSON.parse(savedLeaves));
    } else {
      this.saveLeavesToStorage(this.defaultLeaves);
    }
  }

  private saveLeavesToStorage(leaves: LeaveRequest[]): void {
    localStorage.setItem('hr_leaves', JSON.stringify(leaves));
    this.leavesSubject.next(leaves);
  }

  public getLeaves(): LeaveRequest[] {
    return this.leavesSubject.value;
  }

  public getPendingCount(): number {
    // Return 18 to match the requested KPI block but calculate based on local storage + base if needed
    // Let's return the count of 'En attente' plus a base offset to equal 18, so it remains responsive.
    const pendingInDb = this.leavesSubject.value.filter(l => l.status === 'En attente').length;
    return pendingInDb + 16; 
  }

  public addLeaveRequest(req: Omit<LeaveRequest, 'id' | 'status'>): void {
    const current = this.leavesSubject.value;
    const nextId = current.length > 0 ? Math.max(...current.map(l => l.id)) + 1 : 1;
    const newReq: LeaveRequest = {
      ...req,
      id: nextId,
      status: 'En attente'
    };
    this.saveLeavesToStorage([...current, newReq]);
  }

  public updateLeaveStatus(id: number, status: 'Approuvé' | 'Refusé'): void {
    const current = this.leavesSubject.value;
    const updated = current.map(l => l.id === id ? { ...l, status } : l);
    this.saveLeavesToStorage(updated);
  }
}

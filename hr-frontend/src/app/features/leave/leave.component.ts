import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LeaveService } from '../../core/services/leave.service';
import { LeaveRequest } from '../../core/models/interfaces';

interface CalendarDay {
  dayNumber: number | null;
  dateStr: string | null;
  leaves: { name: string; type: string }[];
}

@Component({
  selector: 'app-leave',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './leave.component.html',
  styleUrl: './leave.component.scss'
})
export class LeaveComponent implements OnInit {
  leaveRequests: LeaveRequest[] = [];
  pendingCount = 18;
  
  weekDays = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];
  calendarDays: CalendarDay[] = [];

  // Modal form state
  showRequestModal = false;
  newRequest: Partial<LeaveRequest> = {
    employeeName: '',
    startDate: '',
    endDate: '',
    type: 'Payé',
    reason: ''
  };

  constructor(private leaveService: LeaveService) {}

  ngOnInit(): void {
    this.leaveService.leaves$.subscribe(data => {
      this.leaveRequests = data;
      this.pendingCount = this.leaveService.getPendingCount();
      this.generateCalendar();
    });
  }

  getLeaveBadgeClass(type: string): string {
    switch (type) {
      case 'Payé': return 'primary';
      case 'Maladie': return 'error';
      case 'RTT': return 'warning';
      default: return 'secondary';
    }
  }

  getLeaveTypeClass(type: string): string {
    switch (type) {
      case 'Payé': return 'c-paye';
      case 'Maladie': return 'c-maladie';
      case 'RTT': return 'c-rtt';
      default: return 'c-sans-solde';
    }
  }

  approve(id: number) {
    this.leaveService.updateLeaveStatus(id, 'Approuvé');
  }

  reject(id: number) {
    this.leaveService.updateLeaveStatus(id, 'Refusé');
  }

  openRequestModal() {
    this.newRequest = {
      employeeName: '',
      startDate: new Date().toISOString().split('T')[0],
      endDate: new Date().toISOString().split('T')[0],
      type: 'Payé',
      reason: ''
    };
    this.showRequestModal = true;
  }

  submitRequest() {
    this.leaveService.addLeaveRequest(this.newRequest as LeaveRequest);
    this.showRequestModal = false;
  }

  private generateCalendar() {
    // Generate June 2026 month calendar (for demonstration)
    // June 1st, 2026 is a Monday. June has 30 days.
    const monthDaysCount = 30;
    const startOffset = 0; // Monday start, offset = 0 days empty

    const days: CalendarDay[] = [];

    // Prepopulate empty cells for offset
    for (let i = 0; i < startOffset; i++) {
      days.push({ dayNumber: null, dateStr: null, leaves: [] });
    }

    // Populate actual days
    for (let day = 1; day <= monthDaysCount; day++) {
      const dateStr = `2026-06-${day.toString().padStart(2, '0')}`;
      const activeLeaves: { name: string; type: string }[] = [];

      // Find if anyone is on approved leave on this date
      this.leaveRequests.forEach(req => {
        if (req.status === 'Approuvé') {
          const start = new Date(req.startDate);
          const end = new Date(req.endDate);
          const current = new Date(dateStr);
          if (current >= start && current <= end) {
            activeLeaves.push({ name: req.employeeName, type: req.type });
          }
        }
      });

      days.push({
        dayNumber: day,
        dateStr,
        leaves: activeLeaves
      });
    }

    this.calendarDays = days;
  }
}

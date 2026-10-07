import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';

import { ManagerTeamService, TeamMember } from '../../core/services/manager-team.service';

@Component({
  selector: 'app-manager-team',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './manager-team.component.html',
  styleUrl: './manager-team.component.scss'
})
export class ManagerTeamComponent implements OnInit, OnDestroy {

  allMembers: TeamMember[] = [];
  filteredMembers: TeamMember[] = [];
  pagedMembers: TeamMember[] = [];

  loading = true;
  error = false;
  errorMessage = '';

  // Pagination
  currentPage = 0;
  pageSize = 10;
  totalPages = 0;

  // Recherche
  searchQuery = '';
  private searchSubject = new Subject<string>();
  private sub = new Subscription();

  constructor(
    private teamService: ManagerTeamService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.sub.add(
      this.searchSubject.pipe(
        debounceTime(300),
        distinctUntilChanged()
      ).subscribe(query => {
        this.searchQuery = query;
        this.currentPage = 0;
        this.applyFilterAndPagination();
      })
    );

    this.loadTeam();
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  loadTeam(): void {
    this.loading = true;
    this.error = false;

    this.sub.add(
      this.teamService.getManagerTeam().subscribe({
        next: (data) => {
          this.allMembers = data || [];
          this.loading = false;
          this.error = false;
          this.applyFilterAndPagination();
        },
        error: (err) => {
          console.error('Erreur lors du chargement de l\'équipe du manager :', err);
          this.loading = false;
          this.error = true;
          this.errorMessage = 'Impossible de charger les membres de votre équipe depuis le serveur.';
          this.allMembers = [];
          this.filteredMembers = [];
          this.pagedMembers = [];
        }
      })
    );
  }

  // Compteurs réels calculés à partir de la BDD
  get totalEmployees(): number {
    return this.allMembers.length;
  }

  get activeEmployeesCount(): number {
    return this.allMembers.filter(m => m.status === 'ACTIVE' || m.status === 'Actif').length;
  }

  get absentTodayCount(): number {
    return this.allMembers.filter(m => m.isOnLeaveToday || m.status === 'ON_LEAVE' || m.status === 'Congé').length;
  }

  applyFilterAndPagination(): void {
    if (!this.searchQuery || !this.searchQuery.trim()) {
      this.filteredMembers = [...this.allMembers];
    } else {
      const q = this.searchQuery.toLowerCase().trim();
      this.filteredMembers = this.allMembers.filter(m =>
        (m.firstName && m.firstName.toLowerCase().includes(q)) ||
        (m.lastName && m.lastName.toLowerCase().includes(q)) ||
        (m.position && m.position.toLowerCase().includes(q)) ||
        (m.email && m.email.toLowerCase().includes(q))
      );
    }

    this.totalPages = Math.ceil(this.filteredMembers.length / this.pageSize) || 1;
    if (this.currentPage >= this.totalPages) {
      this.currentPage = Math.max(0, this.totalPages - 1);
    }

    const start = this.currentPage * this.pageSize;
    this.pagedMembers = this.filteredMembers.slice(start, start + this.pageSize);
  }

  onSearch(): void {
    this.searchSubject.next(this.searchQuery);
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.currentPage = 0;
    this.applyFilterAndPagination();
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages) return;
    this.currentPage = page;
    this.applyFilterAndPagination();
  }

  onPageSizeChange(): void {
    this.currentPage = 0;
    this.applyFilterAndPagination();
  }

  getPageRange(): number[] {
    const range: number[] = [];
    const delta = 2;
    const start = Math.max(0, this.currentPage - delta);
    const end = Math.min(this.totalPages - 1, this.currentPage + delta);
    for (let i = start; i <= end; i++) {
      range.push(i);
    }
    return range;
  }

  viewDetail(memberId: number): void {
    this.router.navigate(['/manager/team', memberId]);
  }

  getInitials(member: TeamMember): string {
    const f = member.firstName?.[0] ?? '';
    const l = member.lastName?.[0] ?? '';
    return (f + l).toUpperCase() || 'EMP';
  }

  getPhotoUrl(id: number): string {
    return this.teamService.getPhotoUrl(id);
  }

  onImgError(event: Event): void {
    (event.target as HTMLImageElement).style.display = 'none';
  }

  getStatusClass(member: TeamMember): string {
    if (member.isOnLeaveToday || member.status === 'ON_LEAVE') return 'status-leave';
    if (member.status === 'INACTIVE' || member.status === 'Inactif') return 'status-inactive';
    return 'status-active';
  }

  getStatusLabel(member: TeamMember): string {
    if (member.isOnLeaveToday) return 'En congé aujourd\'hui';
    if (member.status === 'ON_LEAVE') return 'En congé';
    if (member.status === 'INACTIVE') return 'Inactif';
    return 'Actif';
  }

  formatDate(dateStr?: string): string {
    if (!dateStr) return '—';
    try {
      return new Date(dateStr).toLocaleDateString('fr-FR', { month: 'short', year: 'numeric' });
    } catch {
      return dateStr;
    }
  }
}

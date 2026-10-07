import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

interface NavItem {
  path: string;
  label: string;
  icon: string;
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss'
})
export class SidebarComponent {
  @Input() isCollapsed = false;

  navItems: NavItem[] = [
    { path: '/dashboard', label: 'Dashboard', icon: 'grid_view' },
    { path: '/dashboard/employees', label: 'Employés', icon: 'group' },
    { path: '/dashboard/team-management', label: 'Gestion des Équipes', icon: 'account_tree' },
    { path: '/dashboard/departments', label: 'Départements', icon: 'corporate_fare' },
    { path: '/dashboard/admin-candidates', label: 'Candidatures', icon: 'assignment_ind' },
    { path: '/dashboard/recruitment-tracking', label: 'Suivi Recrutement', icon: 'alt_route' },
    { path: '/dashboard/admin-job-offers', label: 'Offres d\'emploi', icon: 'work' },
    { path: '/dashboard/leaves', label: 'Congés', icon: 'calendar_today' },
    { path: '/dashboard/admin-trainings', label: 'Gestion Formations', icon: 'edit_document' },
    { path: '/dashboard/settings', label: 'Paramètres', icon: 'settings' }
  ];

  constructor(private authService: AuthService) {
    this.filterNavItems();
  }

  filterNavItems() {
    const hasAdminOrRH = this.authService.isAdmin() || this.authService.isRH();

    if (!hasAdminOrRH) {
      // Cas EMPLOYEE (les MANAGER sont déjà redirigés vers /manager)
      this.navItems = [
        { path: '/dashboard/employee', label: 'Tableau de bord', icon: 'grid_view' },
        { path: '/dashboard/leave', label: 'Mes Congés', icon: 'calendar_today' },
        { path: '/dashboard/training', label: 'Formations', icon: 'school' },
        { path: '/dashboard/evaluations', label: 'Mes Évaluations', icon: 'star' },
        { path: '/dashboard/settings', label: 'Paramètres', icon: 'settings' }
      ];
    }
  }

  toggleCollapse() {
    this.isCollapsed = !this.isCollapsed;
  }
}

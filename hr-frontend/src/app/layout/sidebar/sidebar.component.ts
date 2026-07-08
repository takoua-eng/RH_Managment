import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';

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
    { path: '/dashboard/departments', label: 'Départements', icon: 'corporate_fare' },
    { path: '/dashboard/recruitment', label: 'Recrutement', icon: 'assignment_ind' },
    { path: '/dashboard/ai-analysis', label: 'Analyse IA', icon: 'psychology' },
    { path: '/dashboard/leave', label: 'Congés', icon: 'calendar_today' },
    { path: '/dashboard/documents', label: 'Documents', icon: 'folder' },
    { path: '/dashboard/training', label: 'Formations', icon: 'school' },
    { path: '/dashboard/evaluations', label: 'Évaluations', icon: 'star' },
    { path: '/dashboard/chatbot', label: 'Chatbot IA', icon: 'forum' },
    { path: '/dashboard/settings', label: 'Paramètres', icon: 'settings' }
  ];

  toggleCollapse() {
    this.isCollapsed = !this.isCollapsed;
  }
}

import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';

interface NavItem {
  path: string;
  label: string;
  icon: string;
}

@Component({
  selector: 'app-manager-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './manager-sidebar.component.html',
  styleUrl: './manager-sidebar.component.scss'
})
export class ManagerSidebarComponent {

  @Input() isCollapsed = false;

  navItems: NavItem[] = [
    { path: '/manager/dashboard', label: 'Tableau de bord',   icon: 'dashboard'        },
    { path: '/manager/team',      label: 'Mon Équipe',        icon: 'group'            },
    { path: '/manager/recruitment', label: 'Recrutement',     icon: 'work'             },
    { path: '/manager/interviews',  label: 'Mes entretiens',  icon: 'event_available'  },
    { path: '/manager/leaves',    label: 'Congés',            icon: 'calendar_today'   },
    { path: '/manager/training',  label: 'Mes Formations',    icon: 'school'           },
    { path: '/manager/team-trainings', label: 'Formations Équipe', icon: 'cast_for_education' },
    { path: '/manager/evaluations', label: 'Évaluations',    icon: 'star'             },
    { path: '/manager/settings',  label: 'Paramètres',        icon: 'settings'         },
  ];

  toggleCollapse(): void {
    this.isCollapsed = !this.isCollapsed;
  }
}

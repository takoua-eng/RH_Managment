import { Component, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { KeycloakService } from '../../keycloak.service';

interface NotificationItem {
  id: number;
  text: string;
  time: string;
  icon: string;
  unread: boolean;
}

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.scss'
})
export class NavbarComponent {

  @Output() toggleSidebar = new EventEmitter<void>();

  showNotifications = false;
  showProfileMenu = false;

  currentUser = {
    name: '',
    email: '',
    role: '',
    photo: 'assets/images/avatar.png'
  };

  notifications: NotificationItem[] = [
    {
      id: 1,
      text: 'Nouvelle candidature reçue : Ahmed Alami (Java)',
      time: 'Il y a 10 min',
      icon: 'assignment_ind',
      unread: true
    },
    {
      id: 2,
      text: 'Sara Benjelloun a soumis une demande de congé RTT',
      time: 'Il y a 1 heure',
      icon: 'calendar_today',
      unread: true
    },
    {
      id: 3,
      text: 'Analyse IA disponible pour cv_jean_devops.pdf',
      time: 'Il y a 3 heures',
      icon: 'psychology',
      unread: false
    }
  ];

  constructor(
    private router: Router,
    private keycloakService: KeycloakService
  ) {

    this.currentUser.name =
      this.keycloakService.getUsername() || '';

    this.currentUser.email =
      this.keycloakService.getEmail() || '';

    this.currentUser.role =
      this.keycloakService.getRoles().join(', ');

    document.addEventListener('click', (e: MouseEvent) => {
      const target = e.target as HTMLElement;

      if (
        !target.closest('.dropdown-wrapper') &&
        !target.closest('.icon-btn') &&
        !target.closest('.profile-trigger')
      ) {
        this.showNotifications = false;
        this.showProfileMenu = false;
      }
    });
  }

  get unreadCount(): number {
    return this.notifications.filter(n => n.unread).length;
  }

  markAllRead(): void {
    this.notifications.forEach(n => n.unread = false);
  }

  readNote(note: NotificationItem): void {
    note.unread = false;
    this.showNotifications = false;
  }

  navigateToSettings(): void {
    this.showProfileMenu = false;
    this.router.navigate(['/dashboard/settings']);
  }

  logout(): void {
    this.showProfileMenu = false;
    this.keycloakService.logout();
  }
}
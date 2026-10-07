import { Component, EventEmitter, Output, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService } from '../../../core/services/notification.service';
import { NotificationItem } from '../../../core/models/interfaces';
import { Subscription, interval } from 'rxjs';

@Component({
  selector: 'app-manager-navbar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './manager-navbar.component.html',
  styleUrl: './manager-navbar.component.scss'
})
export class ManagerNavbarComponent implements OnInit, OnDestroy {

  @Output() toggleSidebar = new EventEmitter<void>();

  showNotifications = false;
  showProfileMenu = false;

  currentUser = {
    id: 0,
    name: '',
    email: '',
    photo: 'assets/images/avatar.png'
  };

  notifications: NotificationItem[] = [];
  private sub = new Subscription();

  constructor(
    private router: Router,
    private authService: AuthService,
    private notificationService: NotificationService
  ) {
    document.addEventListener('click', (e: MouseEvent) => {
      const target = e.target as HTMLElement;
      if (
        !target.closest('.dropdown-wrapper') &&
        !target.closest('.icon-btn') &&
        !target.closest('.profile-trigger')
      ) {
        this.showNotifications = false;
        this.showProfileMenu   = false;
      }
    });
  }

  ngOnInit(): void {
    this.sub.add(
      this.authService.currentUser$.subscribe(session => {
        if (session) {
          this.currentUser.id = session.id || 0;
          this.currentUser.name  = session.name;
          this.currentUser.email = session.email;
          this.currentUser.photo = session.photo;
          if (this.currentUser.id) {
            this.loadNotifications();
          }
        } else {
          this.currentUser.name  = this.authService.getUsername() || '';
          this.currentUser.email = this.authService.getEmail() || '';
          this.currentUser.photo = 'assets/images/avatar.png';
        }
      })
    );

    // Subscribe to centralized real-time notification store
    this.sub.add(
      this.notificationService.notifications$.subscribe(data => {
        this.notifications = data || [];
      })
    );
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  loadNotifications(): void {
    this.sub.add(
      this.notificationService.getMyNotifications(20).subscribe({
        next: (data) => this.notifications = data || [],
        error: (err) => console.error('Erreur chargement notifications', err)
      })
    );
  }

  get unreadCount(): number {
    return this.notifications.filter(n => n.unread).length;
  }

  get unreadCountDisplay(): string {
    const count = this.unreadCount;
    return count > 99 ? '99+' : count.toString();
  }

  markAllRead(): void {
    this.notificationService.markMyAllAsRead().subscribe(() => {
      this.notifications.forEach(n => (n.unread = false));
    });
  }

  readNote(note: NotificationItem): void {
    const navigateToTarget = () => {
      this.showNotifications = false;
      if (note.link) {
        this.router.navigateByUrl(note.link);
      } else if (note.icon === 'calendar_today') {
        this.router.navigate(['/manager/leaves']);
      } else if (note.icon === 'school') {
        this.router.navigate(['/manager/team-trainings']);
      } else if (note.icon === 'star') {
        this.router.navigate(['/manager/evaluations']);
      } else if (note.icon === 'groups') {
        this.router.navigate(['/manager/team']);
      } else if (note.icon === 'person_search' || note.icon === 'assignment_ind') {
        this.router.navigate(['/manager/recruitment']);
      } else if (note.icon === 'event') {
        this.router.navigate(['/manager/interviews']);
      }
    };

    if (!note.unread) {
      navigateToTarget();
      return;
    }
    
    this.notificationService.markMyAsRead(note.id).subscribe({
      next: () => {
        note.unread = false;
        navigateToTarget();
      },
      error: (err) => {
        console.error('Erreur lors du marquage de la notification:', err);
        navigateToTarget();
      }
    });
  }

  navigateToProfile(): void {
    this.showProfileMenu = false;
    this.router.navigate(['/manager/settings']);
  }

  navigateToSettings(): void {
    this.showProfileMenu = false;
    this.router.navigate(['/manager/settings']);
  }

  logout(): void {
    this.showProfileMenu = false;
    this.authService.logout();
  }
}

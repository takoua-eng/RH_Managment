import { Component, EventEmitter, Output, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { LeaveService } from '../../core/services/leave.service';
import { NotificationItem } from '../../core/models/interfaces';
import { Subscription, interval } from 'rxjs';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.scss'
})
export class NavbarComponent implements OnInit, OnDestroy {

  @Output() toggleSidebar = new EventEmitter<void>();

  showNotifications = false;
  showProfileMenu = false;

  currentUser = {
    id: 0,
    name: '',
    email: '',
    role: '',
    photo: 'assets/images/avatar.png'
  };

  notifications: NotificationItem[] = [];
  private sub = new Subscription();

  constructor(
    private router: Router,
    private authService: AuthService,
    private notificationService: NotificationService,
    private leaveService: LeaveService
  ) {
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

  ngOnInit(): void {
    this.sub.add(
      this.authService.currentUser$.subscribe(session => {
        if (session) {
          this.currentUser.id = session.id || 0;
          this.currentUser.name = session.name;
          this.currentUser.email = session.email;
          this.currentUser.role = session.role;
          this.currentUser.photo = session.photo;
        } else {
          this.currentUser.name = this.authService.getUsername() || '';
          this.currentUser.email = this.authService.getEmail() || '';
          this.currentUser.role = this.authService.getRoles().join(', ');
          this.currentUser.photo = 'assets/images/avatar.png';
        }
        this.loadNotifications();
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
        next: (data) => {
          this.notifications = data || [];
        },
        error: () => {
          this.notifications = [];
        }
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
      this.notifications.forEach(n => n.unread = false);
    });
  }

  readNote(note: NotificationItem): void {
    const navigateToTarget = () => {
      this.showNotifications = false;
      if (note.link) {
        this.router.navigateByUrl(note.link);
      } else if (note.icon === 'calendar_today') {
        this.router.navigate(['/dashboard/leaves']);
      } else if (note.icon === 'school') {
        this.router.navigate(['/dashboard/training']);
      } else if (note.icon === 'star') {
        this.router.navigate(['/dashboard/evaluations']);
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
      error: () => {
        note.unread = false;
        navigateToTarget();
      }
    });
  }

  navigateToSettings(): void {
    this.showProfileMenu = false;
    this.router.navigate(['/dashboard/settings']);
  }

  logout(): void {
    this.showProfileMenu = false;
    this.authService.logout();
  }
}
import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, Subscription, interval, map, tap } from 'rxjs';
import { NotificationItem } from '../models/interfaces';
import { NotificationSocketService } from './notification-socket.service';
import { ToastNotificationService } from './toast-notification.service';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class NotificationService implements OnDestroy {
  private apiUrl = `${environment.apiUrl || 'http://localhost:8087/api'}/notifications`;

  private notificationsSubject = new BehaviorSubject<NotificationItem[]>([]);
  public notifications$: Observable<NotificationItem[]> = this.notificationsSubject.asObservable();

  public unreadCount$: Observable<number> = this.notifications$.pipe(
    map(list => list.filter(n => n.unread).length)
  );

  private pollingSub: Subscription | null = null;
  private socketSubs = new Subscription();

  constructor(
    private http: HttpClient,
    private socketService: NotificationSocketService,
    private toastService: ToastNotificationService
  ) {
    this.initSocketIntegration();
  }

  ngOnDestroy(): void {
    this.stopPolling();
    this.socketSubs.unsubscribe();
  }

  private initSocketIntegration(): void {
    // Watch socket connection status to toggle polling fallback
    this.socketSubs.add(
      this.socketService.connected$.subscribe(isConnected => {
        if (isConnected) {
          this.stopPolling();
        } else {
          this.startPolling();
        }
      })
    );

    // Watch incoming real-time notifications via WebSocket
    this.socketSubs.add(
      this.socketService.newNotification$.subscribe(newNotif => {
        this.addNotificationLocally(newNotif);
        
        // Show discrete toast notification for 5 seconds (5000ms)
        this.toastService.show({
          type: 'info',
          title: 'Notification RH',
          message: newNotif.text,
          duration: 5000,
          link: newNotif.link
        });
      })
    );
  }

  private startPolling(): void {
    if (this.pollingSub) return;
    this.pollingSub = interval(30000).subscribe(() => {
      this.loadNotifications(20).subscribe();
    });
  }

  private stopPolling(): void {
    if (this.pollingSub) {
      this.pollingSub.unsubscribe();
      this.pollingSub = null;
    }
  }

  public loadNotifications(limit: number = 20): Observable<NotificationItem[]> {
    return this.http.get<NotificationItem[]>(`${this.apiUrl}/me?limit=${limit}`).pipe(
      tap(list => {
        this.notificationsSubject.next(list || []);
      })
    );
  }

  public addNotificationLocally(notif: NotificationItem): void {
    const current = this.notificationsSubject.value;
    const exists = current.some(n => n.id === notif.id);
    if (exists) {
      const updated = current.map(n => n.id === notif.id ? notif : n);
      this.notificationsSubject.next(updated);
    } else {
      this.notificationsSubject.next([notif, ...current]);
    }
  }

  // ==========================================
  // LOGGED-IN USER NOTIFICATIONS (/api/notifications/me)
  // ==========================================
  getMyNotifications(limit: number = 20): Observable<NotificationItem[]> {
    return this.loadNotifications(limit);
  }

  getUnreadCount(): Observable<{ unreadCount: number }> {
    return this.http.get<{ unreadCount: number }>(`${this.apiUrl}/me/unread-count`);
  }

  markMyAsRead(notificationId: number): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/${notificationId}/read`, {}).pipe(
      tap(() => {
        const updated = this.notificationsSubject.value.map(n => {
          if (n.id === notificationId) {
            return { ...n, unread: false };
          }
          return n;
        });
        this.notificationsSubject.next(updated);
      })
    );
  }

  markMyAllAsRead(): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/me/read-all`, {}).pipe(
      tap(() => {
        const updated = this.notificationsSubject.value.map(n => ({ ...n, unread: false }));
        this.notificationsSubject.next(updated);
      })
    );
  }

  // ==========================================
  // LEGACY COMPATIBILITY METHODS
  // ==========================================
  getNotifications(managerId?: number): Observable<NotificationItem[]> {
    return this.getMyNotifications();
  }

  markAllAsRead(managerId?: number): Observable<void> {
    return this.markMyAllAsRead();
  }

  markAsRead(managerIdOrNotifId: number, notifId?: number): Observable<void> {
    const idToMark = notifId !== undefined ? notifId : managerIdOrNotifId;
    return this.markMyAsRead(idToMark);
  }

  getEmployeeNotifications(employeeId?: number): Observable<NotificationItem[]> {
    return this.getMyNotifications();
  }

  markAllEmployeeAsRead(employeeId?: number): Observable<void> {
    return this.markMyAllAsRead();
  }

  markEmployeeAsRead(employeeIdOrNotifId: number, notifId?: number): Observable<void> {
    const idToMark = notifId !== undefined ? notifId : employeeIdOrNotifId;
    return this.markMyAsRead(idToMark);
  }
}

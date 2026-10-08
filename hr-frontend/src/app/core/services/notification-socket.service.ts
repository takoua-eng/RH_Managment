import { Injectable } from '@angular/core';
import { RxStomp, RxStompState } from '@stomp/rx-stomp';
import { BehaviorSubject, Observable, Subject, map } from 'rxjs';
import { AuthService } from './auth.service';
import { NotificationItem } from '../models/interfaces';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class NotificationSocketService {
  private rxStomp: RxStomp;
  private newNotificationSubject = new Subject<NotificationItem>();
  public newNotification$: Observable<NotificationItem> = this.newNotificationSubject.asObservable();

  private connectedSubject = new BehaviorSubject<boolean>(false);
  public connected$: Observable<boolean> = this.connectedSubject.asObservable();

  private isInitialized = false;

  constructor(private authService: AuthService) {
    this.rxStomp = new RxStomp();

    // Listen to connection state changes
    this.rxStomp.connectionState$.pipe(
      map(state => state === RxStompState.OPEN)
    ).subscribe(isOpen => {
      this.connectedSubject.next(isOpen);
    });

    // Automatically manage socket based on auth user session
    this.authService.currentUser$.subscribe(user => {
      if (user && this.authService.isLoggedIn()) {
        this.connect();
      } else {
        this.disconnect();
      }
    });
  }

  public connect(): void {
    if (this.isInitialized && this.rxStomp.active) {
      return;
    }

    const token = this.authService.getToken();
    if (!token) {
      return;
    }

    this.rxStomp.configure({
      brokerURL: environment.wsUrl || 'environment.wsUrl',
      connectHeaders: {
        Authorization: `Bearer ${token}`
      },
      heartbeatIncoming: 0,
      heartbeatOutgoing: 20000,
      reconnectDelay: 5000,
      debug: (msg: string) => {
        // Log STOMP debug messages in dev mode if needed
      },
      beforeConnect: async () => {
        if (!this.authService.isLoggedIn()) {
          const refreshed = await this.authService.refreshToken().toPromise();
          if (!refreshed) {
            return;
          }
        }
        const currentToken = this.authService.getToken();
        if (currentToken && this.rxStomp.stompClient) {
          this.rxStomp.configure({
            connectHeaders: {
              Authorization: `Bearer ${currentToken}`
            }
          });
        }
      }
    });

    this.rxStomp.activate();
    this.isInitialized = true;

    // Subscribe to destination /user/queue/notifications
    this.rxStomp.watch('/user/queue/notifications').subscribe({
      next: (message) => {
        try {
          const body = JSON.parse(message.body);
          const notificationItem: NotificationItem = {
            id: body.id,
            text: body.text,
            time: body.time || "À l'instant",
            icon: body.icon || 'notifications',
            unread: body.unread !== undefined ? body.unread : true,
            type: body.type,
            link: body.link
          };
          this.newNotificationSubject.next(notificationItem);
        } catch (err) {
          console.error('[WebSocket] Error parsing received notification JSON:', err);
        }
      },
      error: (err) => console.error('[WebSocket] Subscription error on /user/queue/notifications:', err)
    });
  }

  public disconnect(): void {
    if (this.isInitialized) {
      this.rxStomp.deactivate();
      this.isInitialized = false;
      this.connectedSubject.next(false);
    }
  }
}

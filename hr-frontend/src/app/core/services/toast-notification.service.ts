import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export interface ToastMessage {
  id: string;
  type: 'success' | 'error' | 'warning' | 'info';
  title?: string;
  message: string;
  duration?: number;
  link?: string;
}

@Injectable({ providedIn: 'root' })
export class ToastNotificationService {
  private toastsSubject = new BehaviorSubject<ToastMessage[]>([]);
  public toasts$: Observable<ToastMessage[]> = this.toastsSubject.asObservable();

  public show(toast: Omit<ToastMessage, 'id'>): void {
    const id = Math.random().toString(36).substring(2, 9);
    const fullToast: ToastMessage = {
      id,
      duration: 4000,
      ...toast
    };

    const currentToasts = this.toastsSubject.value;
    this.toastsSubject.next([...currentToasts, fullToast]);

    if (fullToast.duration && fullToast.duration > 0) {
      setTimeout(() => {
        this.remove(id);
      }, fullToast.duration);
    }
  }

  public success(message: string, title: string = 'Succès'): void {
    this.show({ type: 'success', title, message });
  }

  public error(message: string, title: string = 'Erreur'): void {
    this.show({ type: 'error', title, message });
  }

  public warning(message: string, title: string = 'Attention'): void {
    this.show({ type: 'warning', title, message });
  }

  public info(message: string, title: string = 'Information'): void {
    this.show({ type: 'info', title, message });
  }

  public remove(id: string): void {
    const updated = this.toastsSubject.value.filter(t => t.id !== id);
    this.toastsSubject.next(updated);
  }
}

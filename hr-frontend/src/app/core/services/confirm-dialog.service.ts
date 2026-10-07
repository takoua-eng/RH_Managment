import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export interface ConfirmOptions {
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  type?: 'danger' | 'warning' | 'info';
}

@Injectable({ providedIn: 'root' })
export class ConfirmDialogService {
  private dialogSubject = new BehaviorSubject<ConfirmOptions | null>(null);
  public dialog$: Observable<ConfirmOptions | null> = this.dialogSubject.asObservable();

  private resolveCallback: ((value: boolean) => void) | null = null;

  public showConfirm(options: ConfirmOptions): Promise<boolean> {
    const fullOptions: ConfirmOptions = {
      confirmText: 'Confirmer',
      cancelText: 'Annuler',
      type: 'danger',
      ...options
    };

    this.dialogSubject.next(fullOptions);

    return new Promise<boolean>((resolve) => {
      this.resolveCallback = resolve;
    });
  }

  public confirm(): void {
    if (this.resolveCallback) {
      this.resolveCallback(true);
      this.resolveCallback = null;
    }
    this.dialogSubject.next(null);
  }

  public cancel(): void {
    if (this.resolveCallback) {
      this.resolveCallback(false);
      this.resolveCallback = null;
    }
    this.dialogSubject.next(null);
  }
}

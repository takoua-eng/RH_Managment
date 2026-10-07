import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ToastNotificationService, ToastMessage } from '../../services/toast-notification.service';

@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './toast-container.component.html',
  styleUrl: './toast-container.component.scss'
})
export class ToastContainerComponent implements OnInit {
  toasts: ToastMessage[] = [];

  constructor(
    private toastService: ToastNotificationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.toastService.toasts$.subscribe(list => {
      this.toasts = list;
    });
  }

  onToastClick(toast: ToastMessage): void {
    if (toast.link) {
      this.router.navigateByUrl(toast.link);
      this.remove(toast.id);
    }
  }

  remove(id: string): void {
    this.toastService.remove(id);
  }
}

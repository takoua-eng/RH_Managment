import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ConfirmDialogService, ConfirmOptions } from '../../services/confirm-dialog.service';

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './confirm-dialog.component.html',
  styleUrl: './confirm-dialog.component.scss'
})
export class ConfirmDialogComponent implements OnInit {
  options: ConfirmOptions | null = null;

  constructor(private confirmService: ConfirmDialogService) {}

  ngOnInit(): void {
    this.confirmService.dialog$.subscribe(opts => {
      this.options = opts;
    });
  }

  onConfirm(): void {
    this.confirmService.confirm();
  }

  onCancel(): void {
    this.confirmService.cancel();
  }
}

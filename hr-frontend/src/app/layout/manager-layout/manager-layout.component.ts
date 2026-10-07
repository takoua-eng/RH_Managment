import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { ManagerSidebarComponent } from './manager-sidebar/manager-sidebar.component';
import { ManagerNavbarComponent } from './manager-navbar/manager-navbar.component';

@Component({
  selector: 'app-manager-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, ManagerSidebarComponent, ManagerNavbarComponent],
  templateUrl: './manager-layout.component.html',
  styleUrl: './manager-layout.component.scss'
})
export class ManagerLayoutComponent {
  sidebarCollapsed = false;
}

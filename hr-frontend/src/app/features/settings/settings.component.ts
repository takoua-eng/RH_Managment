import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss'
})
export class SettingsComponent implements OnInit {
  activeTab: 'profile' | 'roles' | 'theme' = 'profile';

  profile = {
    firstName: 'Marc',
    lastName: 'Dubois',
    email: 'admin@corp.com',
    position: 'Directeur des Ressources Humaines'
  };

  darkMode = false;

  ngOnInit(): void {
    // Read theme state
    this.darkMode = document.body.classList.contains('dark-theme');
    
    const saved = localStorage.getItem('hr_admin_profile');
    if (saved) {
      this.profile = JSON.parse(saved);
    }
  }

  saveProfile() {
    localStorage.setItem('hr_admin_profile', JSON.stringify(this.profile));
    alert('Profil sauvegardé avec succès !');
  }

  onThemeToggle() {
    if (this.darkMode) {
      document.body.classList.add('dark-theme');
      localStorage.setItem('theme', 'dark');
    } else {
      document.body.classList.remove('dark-theme');
      localStorage.setItem('theme', 'light');
    }
  }
}

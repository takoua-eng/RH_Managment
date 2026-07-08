import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  mode: 'login' | 'forgot' = 'login';
  email = '';
  password = '';
  rememberMe = true;
  forgotEmail = '';
  loading = false;

  alertMsg = '';
  alertClass = '';

  constructor(private authService: AuthService, private router: Router) {}
loginWithKeycloak() {
  this.authService.loginWithKeycloak();
}

  fillDemoCredentials() {
    this.email = 'admin@corp.com';
    this.password = 'password123';
  }

  handleLogin() {
    if (!this.email || !this.password) {
      this.showAlert('Veuillez remplir tous les champs.', 'alert-danger');
      return;
    }

    this.loading = true;
    this.alertMsg = '';
    
    this.authService.login(this.email, this.password).subscribe({
      next: (success) => {
        this.loading = false;
        if (success) {
          this.router.navigate(['/dashboard']);
        } else {
          this.showAlert('Identifiants invalides.', 'alert-danger');
        }
      },
      error: () => {
        this.loading = false;
        this.showAlert('Une erreur s\'est produite. Veuillez réessayer.', 'alert-danger');
      }
    });
  }

  handleForgot() {
    if (!this.forgotEmail) {
      this.showAlert('Veuillez saisir votre adresse email.', 'alert-danger');
      return;
    }

    this.loading = true;
    this.alertMsg = '';

    this.authService.forgotPassword(this.forgotEmail).subscribe({
      next: () => {
        this.loading = false;
        this.showAlert('Instructions de réinitialisation envoyées à votre adresse email.', 'alert-success');
        setTimeout(() => {
          this.mode = 'login';
          this.alertMsg = '';
        }, 2000);
      },
      error: () => {
        this.loading = false;
        this.showAlert('Une erreur s\'est produite. Veuillez réessayer.', 'alert-danger');
      }
    });
  }

  private showAlert(msg: string, className: string) {
    this.alertMsg = msg;
    this.alertClass = className;
  }
}

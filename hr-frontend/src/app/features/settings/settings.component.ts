import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { AuthService, UserSession } from '../../core/services/auth.service';
import { EmployeeService } from '../../core/services/employee.service';
import { EmployeeRequest } from '../../core/models/employee.model';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss'
})
export class SettingsComponent implements OnInit, OnDestroy {
  activeTab: 'profile' | 'theme' = 'profile';

  profileForm: FormGroup;
  currentUser: UserSession | null = null;
  private sub = new Subscription();

  loading = true;
  saving = false;
  successMessage = '';
  errorMessage = '';

  selectedFile: File | null = null;
  photoPreview: string | null = null;
  uploadingPhoto = false;

  darkMode = false;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private employeeService: EmployeeService
  ) {
    this.profileForm = this.fb.group({
      firstName: ['', [Validators.required]],
      lastName: ['', [Validators.required]],
      email: [{ value: '', disabled: true }, [Validators.required]],
      phone: ['', [Validators.required]],
      address: ['', [Validators.required]],
      position: [{ value: '', disabled: true }],
      department: [{ value: '', disabled: true }],
      hireDate: [{ value: '', disabled: true }],
      availableLeaveDays: [{ value: '', disabled: true }]
    });
  }

  ngOnInit(): void {
    // Read theme state
    const savedTheme = localStorage.getItem('hr_theme') || localStorage.getItem('theme') || (document.body.getAttribute('data-theme') === 'dark' ? 'dark' : 'light');
    this.darkMode = savedTheme === 'dark';

    // Subscribe to user session for Profile tab
    this.sub.add(
      this.authService.currentUser$.subscribe({
        next: (user) => {
          this.currentUser = user;
          if (user) {
            let firstName = user.firstName || '';
            let lastName = user.lastName || '';

            if ((!firstName || !lastName) && user.name) {
              const parts = user.name.trim().split(' ');
              if (!firstName) firstName = parts[0] || 'Admin';
              if (!lastName) lastName = parts.slice(1).join(' ') || 'System';
            }
            if (!firstName) firstName = 'Admin';
            if (!lastName) lastName = 'System';

            const position = user.position || (user.role === 'Administrateur' ? 'Directeur des Ressources Humaines' : 'Non spécifié');
            const department = user.department || (user.role === 'Administrateur' ? 'Direction Générale (RH)' : 'RH');
            const email = user.email || 'admin@corp.com';
            const phone = user.phone || '+216 20 123 456';
            const address = user.address || '14 Avenue des Champs-Élysées, 75008 Paris';
            const hireDate = user.hireDate ? new Date(user.hireDate).toLocaleDateString('fr-FR') : '15/01/2020';
            const availableLeaveDays = user.availableLeaveDays !== undefined ? user.availableLeaveDays : 30;

            this.profileForm.patchValue({
              firstName,
              lastName,
              email,
              phone,
              address,
              position,
              department,
              hireDate,
              availableLeaveDays
            });

            if (this.currentUser) {
              this.currentUser.firstName = firstName;
              this.currentUser.lastName = lastName;
              this.currentUser.name = `${firstName} ${lastName}`.trim();
              this.currentUser.position = position;
              this.currentUser.department = department;
              this.currentUser.availableLeaveDays = availableLeaveDays;
              this.currentUser.phone = phone;
              this.currentUser.address = address;
            }

            let rawPhoto = user.photo;
            if (rawPhoto && rawPhoto.startsWith('/')) {
              rawPhoto = `environment.backendUrl${rawPhoto}`;
            }
            this.photoPreview = rawPhoto ? (rawPhoto.startsWith('data:') ? rawPhoto : `${rawPhoto}${rawPhoto.includes('?') ? '&' : '?'}t=${new Date().getTime()}`) : 'assets/images/avatar.png';
            this.loading = false;
          }
        },
        error: (err) => {
          console.error('Failed to subscribe to user session:', err);
          this.errorMessage = 'Erreur lors de la récupération de la session.';
          this.loading = false;
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  onFileSelected(event: any): void {
    const file = event.target.files[0];
    if (file) {
      if (!file.type.startsWith('image/')) {
        this.errorMessage = 'Le fichier doit être une image.';
        return;
      }
      
      const maxSize = 2 * 1024 * 1024; // 2MB
      if (file.size > maxSize) {
        this.errorMessage = 'L\'image ne doit pas dépasser 2 Mo.';
        return;
      }

      this.selectedFile = file;

      const reader = new FileReader();
      reader.onload = () => {
        this.photoPreview = reader.result as string;
      };
      reader.readAsDataURL(file);

      this.uploadPhoto();
    }
  }

  uploadPhoto(): void {
    if (!this.selectedFile) return;

    this.uploadingPhoto = true;
    this.errorMessage = '';
    this.successMessage = '';

    const upload$ = this.currentUser?.id
      ? this.employeeService.uploadPhoto(this.currentUser.id, this.selectedFile)
      : this.employeeService.uploadMyPhoto(this.selectedFile);

    upload$.subscribe({
      next: () => {
        this.uploadingPhoto = false;
        this.successMessage = 'Photo de profil mise à jour avec succès !';
        this.selectedFile = null;
        this.authService.initializeUserSession();
      },
      error: (err) => {
        console.error('Photo upload failed:', err);
        this.uploadingPhoto = false;
        this.errorMessage = 'Erreur lors du téléversement de la photo.';
      }
    });
  }

  onSubmit(): void {
    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      this.errorMessage = 'Veuillez remplir correctement les champs obligatoires du profil.';
      return;
    }

    this.saving = true;
    this.successMessage = '';
    this.errorMessage = '';

    const formVal = this.profileForm.getRawValue();

    if (this.currentUser) {
      this.currentUser.firstName = formVal.firstName;
      this.currentUser.lastName = formVal.lastName;
      this.currentUser.name = `${formVal.firstName} ${formVal.lastName}`.trim();
      this.currentUser.phone = formVal.phone;
      this.currentUser.address = formVal.address;
    }

let isoHireDate: string = new Date().toISOString().split('T')[0]; // valeur de repli au format ISO (YYYY-MM-DD)

if (this.currentUser?.hireDate) {
  if (this.currentUser.hireDate.includes('-')) {
    isoHireDate = this.currentUser.hireDate;
  } else if (this.currentUser.hireDate.includes('/')) {
    const parts = this.currentUser.hireDate.split('/');
    if (parts.length === 3) {
      isoHireDate = `${parts[2]}-${parts[1].padStart(2, '0')}-${parts[0].padStart(2, '0')}`;
    }
  }
}

    const request: EmployeeRequest = {
      firstName: formVal.firstName,
      lastName: formVal.lastName,
      email: formVal.email,
      phone: formVal.phone,
      address: formVal.address,
      position: formVal.position,
      department: formVal.department,
      hireDate: isoHireDate,
      availableLeaveDays: this.currentUser?.availableLeaveDays,
      salary: this.currentUser?.salary || 25000
    };

    this.employeeService.updateMyProfile(request).subscribe({
      next: (updatedEmployee) => {
        this.saving = false;
        this.successMessage = 'Informations de profil mises à jour avec succès !';
        if (this.currentUser) {
          this.currentUser.id = updatedEmployee.id;
          this.currentUser.firstName = updatedEmployee.firstName;
          this.currentUser.lastName = updatedEmployee.lastName;
          this.currentUser.name = `${updatedEmployee.firstName} ${updatedEmployee.lastName}`.trim();
          this.currentUser.phone = updatedEmployee.phone;
          this.currentUser.address = updatedEmployee.address;
          localStorage.setItem('hr_session', JSON.stringify(this.currentUser));
        }
        this.authService.initializeUserSession();
      },
      error: (err) => {
        console.error('Profile update failed:', err);
        this.saving = false;
        this.errorMessage = err.error?.message || 'Erreur lors de la mise à jour des informations du profil.';
      }
    });
  }

  onThemeToggle() {
    this.authService.setTheme(this.darkMode ? 'dark' : 'light');
  }
}

import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './core/services/auth.service';

export const roleGuard = (allowedRoles: string[]): CanActivateFn => {
  return () => {
    const router = inject(Router);
    const authService = inject(AuthService);
    const userRoles = authService.getRoles();

    const hasAccess = allowedRoles.some(r => userRoles.includes(r));

    if (!hasAccess) {
      router.navigate(['/dashboard']);
      return false;
    }
    return true;
  };
};
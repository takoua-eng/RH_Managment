import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import keycloak from './keycloak.config';

export const roleGuard = (allowedRoles: string[]): CanActivateFn => {
  return () => {
    const router = inject(Router);
    const roles: string[] = keycloak.realmAccess?.roles ?? [];
    const hasAccess = allowedRoles.some(r => roles.includes(r));

    if (!hasAccess) {
      router.navigate(['/unauthorized']);
      return false;
    }
    return true;
  };
};
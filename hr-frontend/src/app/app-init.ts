import { inject } from '@angular/core';
import keycloak from './keycloak.config';
import { AuthService } from './core/services/auth.service';

export function initializeKeycloak(): () => Promise<boolean> {
  return () =>
    keycloak.init({
      onLoad: 'check-sso',
      silentCheckSsoRedirectUri: window.location.origin + '/assets/silent-check-sso.html',
      pkceMethod: 'S256'
    });
}

export function appInitializerFactory() {
  const authService = inject(AuthService);
  return async () => {
    await initializeKeycloak()();
    if (keycloak.authenticated) {
      authService.initializeUserSession();
    } else {
      await authService.tryRestoreSession();
    }
  };
}
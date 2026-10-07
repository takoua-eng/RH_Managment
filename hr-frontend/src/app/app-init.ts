import { inject } from '@angular/core';
import { AuthService } from './core/services/auth.service';

export function appInitializerFactory() {
  const authService = inject(AuthService);
  return async () => {
    await authService.tryRestoreSession();
  };
}
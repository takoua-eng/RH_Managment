import { CanActivateFn } from '@angular/router';
import keycloak from './keycloak.config';
export const authGuard: CanActivateFn = () => {
  if (keycloak.authenticated) {
    return true;
  }
  keycloak.login();
  return false;
};

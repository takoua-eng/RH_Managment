import { Injectable } from '@angular/core';
import keycloak from './keycloak.config';

@Injectable({
  providedIn: 'root'
})
export class KeycloakService {

  getToken(): string {
    return keycloak.token || '';
  }

getUsername(): string {
  return keycloak.profile?.username || '';
}

  getEmail(): string {
    return (
      keycloak.tokenParsed?.['email'] as string
    ) || '';
  }

  getFirstName(): string {
    return (
      keycloak.tokenParsed?.['given_name'] as string
    ) || '';
  }

  getLastName(): string {
    return (
      keycloak.tokenParsed?.['family_name'] as string
    ) || '';
  }

  getFullName(): string {

    const firstName = this.getFirstName();
    const lastName = this.getLastName();

    return `${firstName} ${lastName}`.trim();
  }

  getRoles(): string[] {

    const realmAccess =
      keycloak.tokenParsed?.['realm_access'] as any;

    return realmAccess?.roles || [];
  }

  hasRole(role: string): boolean {
    return this.getRoles().includes(role);
  }

  isAdmin(): boolean {
    return this.hasRole('ADMIN');
  }

  isRH(): boolean {
    return this.hasRole('RH');
  }

  isManager(): boolean {
    return this.hasRole('MANAGER');
  }

  isEmploye(): boolean {
    return this.hasRole('EMPLOYE');
  }

  isLoggedIn(): boolean {
    return !!keycloak.authenticated;
  }

  logout(): void {

    keycloak.logout({
      redirectUri: 'http://localhost:4200'
    });

  }

  async refreshToken(): Promise<boolean> {

    try {

      await keycloak.updateToken(30);

      return true;

    } catch (error) {

      console.error(
        'Erreur refresh token',
        error
      );

      return false;
    }
  }
}
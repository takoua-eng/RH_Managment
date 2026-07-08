import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable, of, catchError, map } from 'rxjs';
import keycloak from '../../keycloak.config';
import { KeycloakService } from '../../keycloak.service';

export interface UserSession {
  email: string;
  name: string;
  role: 'Administrateur' | 'Manager' | 'RH' | 'Employé';
  photo: string;
}

interface TokenResponse {
  access_token: string;
  refresh_token: string;
  expires_in: number;
}

const KEYCLOAK_URL = 'http://localhost:9090';
const REALM = 'hr-realm';
const CLIENT_ID = 'hr-angular';
const REFRESH_TOKEN_KEY = 'hr_refresh_token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private currentUserSubject = new BehaviorSubject<UserSession | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  private themeSubject = new BehaviorSubject<'light' | 'dark'>('light');
  public theme$ = this.themeSubject.asObservable();

  private refreshTimer: any;

  constructor(private http: HttpClient, private kc: KeycloakService) {
    this.initializeUserSession();
    const savedTheme = (localStorage.getItem('hr_theme') as 'light' | 'dark') || 'light';
    this.setTheme(savedTheme);
  }

  /** Recalcule la session applicative à partir de l'état actuel de Keycloak en consommant le backend */
  public initializeUserSession(): void {
    if (!keycloak.authenticated) {
      localStorage.removeItem('hr_session');
      this.currentUserSubject.next(null);
      return;
    }

    this.http.get<any>('http://localhost:8087/api/me').pipe(
      map(user => {
        const email = user.email || this.kc.getEmail() || 'user@corp.com';
        const name = user.name || (user.firstName && user.lastName ? `${user.firstName} ${user.lastName}` : '') || this.kc.getFullName() || this.kc.getUsername() || 'Utilisateur';
        
        let role: UserSession['role'] = 'Employé';
        const rawRole = (user.role || '').toUpperCase();
        if (rawRole === 'ADMIN' || rawRole === 'ADMINISTRATEUR') role = 'Administrateur';
        else if (rawRole === 'RH') role = 'RH';
        else if (rawRole === 'MANAGER') role = 'Manager';
        else {
          if (this.kc.isAdmin()) role = 'Administrateur';
          else if (this.kc.isRH()) role = 'RH';
          else if (this.kc.isManager()) role = 'Manager';
        }

        const session: UserSession = {
          email,
          name,
          role,
          photo: user.photo || 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150'
        };

        localStorage.setItem('hr_session', JSON.stringify(session));
        this.currentUserSubject.next(session);
      }),
      catchError(err => {
        console.error('Error fetching user from /api/me, falling back to Keycloak:', err);
        const email = this.kc.getEmail() || 'user@corp.com';
        const name = this.kc.getFullName() || this.kc.getUsername() || 'Utilisateur';

        let role: UserSession['role'] = 'Employé';
        if (this.kc.isAdmin()) role = 'Administrateur';
        else if (this.kc.isRH()) role = 'RH';
        else if (this.kc.isManager()) role = 'Manager';

        const session: UserSession = {
          email, name, role,
          photo: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150'
        };
        localStorage.setItem('hr_session', JSON.stringify(session));
        this.currentUserSubject.next(session);
        return of(null);
      })
    ).subscribe();

    this.scheduleTokenRefresh();
  }

  public get currentUserValue(): UserSession | null {
    return this.currentUserSubject.value;
  }

  /** Bouton "Se connecter avec Keycloak" — Standard Flow, redirection hébergée */
public loginWithKeycloak(loginHint?: string): void {
  keycloak.login({
    redirectUri: window.location.origin + '/dashboard',
    loginHint: loginHint
  });
}
  /** Formulaire local — Direct Access Grant contre l'endpoint token de Keycloak */
  public login(email: string, password: string): Observable<boolean> {
    const body = new URLSearchParams();
    body.set('client_id', CLIENT_ID);
    body.set('grant_type', 'password');
    body.set('username', email);
    body.set('password', password);

    const headers = new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' });
    const url = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`;

    return this.http.post<TokenResponse>(url, body.toString(), { headers }).pipe(
      map(res => { this.applyTokenResponse(res); return true; }),
      catchError(() => of(false))
    );
  }

  public forgotPassword(email: string): Observable<boolean> {
    return new Observable<boolean>(subscriber => {
      setTimeout(() => { subscriber.next(true); subscriber.complete(); }, 800);
    });
  }

  public logout(): void {
    localStorage.removeItem('hr_session');
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    clearTimeout(this.refreshTimer);
    this.currentUserSubject.next(null);
    keycloak.logout({ redirectUri: window.location.origin });
  }

  public toggleTheme(): void {
    this.setTheme(this.themeSubject.value === 'light' ? 'dark' : 'light');
  }

  public setTheme(theme: 'light' | 'dark'): void {
    this.themeSubject.next(theme);
    localStorage.setItem('hr_theme', theme);
    document.body.setAttribute('data-theme', theme);
  }

  /** Injecte manuellement les tokens obtenus dans l'instance Keycloak partagée */
  private applyTokenResponse(res: TokenResponse): void {
    keycloak.token = res.access_token;
    keycloak.refreshToken = res.refresh_token;
    keycloak.tokenParsed = this.decodeJwt(res.access_token);
    keycloak.refreshTokenParsed = this.decodeJwt(res.refresh_token);
    keycloak.authenticated = true;
    keycloak.realmAccess = keycloak.tokenParsed?.['realm_access'];
    keycloak.subject = keycloak.tokenParsed?.['sub'];

    localStorage.setItem(REFRESH_TOKEN_KEY, res.refresh_token);
    this.initializeUserSession();
  }

  private decodeJwt(token: string): any {
    const payload = token.split('.')[1];
    const decoded = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(decoded);
  }

  /** Restaure la session après un F5, via le refresh_token stocké */
  public tryRestoreSession(): Promise<void> {
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (!refreshToken) return Promise.resolve();

    const body = new URLSearchParams();
    body.set('client_id', CLIENT_ID);
    body.set('grant_type', 'refresh_token');
    body.set('refresh_token', refreshToken);

    const headers = new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' });
    const url = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`;

    return this.http.post<TokenResponse>(url, body.toString(), { headers }).toPromise()
      .then(res => { if (res) this.applyTokenResponse(res); })
      .catch(() => localStorage.removeItem(REFRESH_TOKEN_KEY));
  }

  /** Planifie un rafraîchissement silencieux ~30s avant expiration */
  private scheduleTokenRefresh(): void {
    clearTimeout(this.refreshTimer);
    const exp = keycloak.tokenParsed?.['exp'];
    if (!exp) return;
    const delay = exp * 1000 - Date.now() - 30000;
    if (delay > 0) this.refreshTimer = setTimeout(() => this.tryRestoreSession(), delay);
  }
}
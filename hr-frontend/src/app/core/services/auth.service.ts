import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, of, catchError, map } from 'rxjs';

export interface UserSession {
  email: string;
  name: string;
  role: 'Administrateur' | 'Manager' | 'RH' | 'Employé';
  photo: string;
  id?: number;
  firstName?: string;
  lastName?: string;
  position?: string;
  department?: string;
  hireDate?: string;
  availableLeaveDays?: number;
  address?: string;
  phone?: string;
  salary?: number;
}

export interface TokenResponse {
  access_token: string;
  refresh_token: string;
  expires_in: number;
  refresh_expires_in?: number;
  token_type?: string;
}

const BACKEND_URL = 'http://localhost:8087/api';
const ACCESS_TOKEN_KEY = 'hr_access_token';
const REFRESH_TOKEN_KEY = 'hr_refresh_token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private currentUserSubject = new BehaviorSubject<UserSession | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  private themeSubject = new BehaviorSubject<'light' | 'dark'>('light');
  public theme$ = this.themeSubject.asObservable();

  private refreshTimer: any;

  constructor(private http: HttpClient, private router: Router) {
    const savedTheme = (localStorage.getItem('hr_theme') as 'light' | 'dark') || 'light';
    this.setTheme(savedTheme);
  }

  /**
   * Connexion via le backend (POST /api/auth/login)
   */
  public login(username: string, password: string): Observable<boolean> {
    return this.http.post<TokenResponse>(`${BACKEND_URL}/auth/login`, { username, password }).pipe(
      map(res => {
        this.saveTokens(res);
        this.initializeUserSession();
        return true;
      }),
      catchError(err => {
        console.error('Erreur de connexion:', err);
        return of(false);
      })
    );
  }

  /**
   * Rafraîchissement du token via le backend (POST /api/auth/refresh)
   */
  public refreshToken(): Observable<boolean> {
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) {
      this.clearSession();
      return of(false);
    }

    return this.http.post<TokenResponse>(`${BACKEND_URL}/auth/refresh`, { refresh_token: refreshToken }).pipe(
      map(res => {
        this.saveTokens(res);
        this.scheduleTokenRefresh();
        return true;
      }),
      catchError(() => {
        this.clearSession();
        return of(false);
      })
    );
  }

  /**
   * Déconnexion via le backend (POST /api/auth/logout)
   */
  public logout(): void {
    const refreshToken = this.getRefreshToken();
    if (refreshToken) {
      this.http.post(`${BACKEND_URL}/auth/logout`, { refresh_token: refreshToken }).subscribe({
        error: err => console.warn('Logout notice:', err)
      });
    }
    this.clearSession();
    this.router.navigate(['/login']);
  }

  public getToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  public getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  public isLoggedIn(): boolean {
    const token = this.getToken();
    if (!token) return false;
    try {
      const decoded = this.decodeJwt(token);
      if (decoded && decoded.exp) {
        return decoded.exp * 1000 > Date.now();
      }
      return true;
    } catch {
      return false;
    }
  }

  public get currentUserValue(): UserSession | null {
    return this.currentUserSubject.value;
  }

  public saveTokens(res: TokenResponse): void {
    if (res.access_token) {
      localStorage.setItem(ACCESS_TOKEN_KEY, res.access_token);
    }
    if (res.refresh_token) {
      localStorage.setItem(REFRESH_TOKEN_KEY, res.refresh_token);
    }
  }

  public clearSession(): void {
    clearTimeout(this.refreshTimer);
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem('hr_session');
    this.currentUserSubject.next(null);
  }

  public decodeJwt(token: string): any {
    try {
      const payload = token.split('.')[1];
      const decoded = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
      return JSON.parse(decoded);
    } catch {
      return null;
    }
  }

  public getRoles(): string[] {
    const token = this.getToken();
    if (!token) return [];
    const decoded = this.decodeJwt(token);
    return decoded?.realm_access?.roles || [];
  }

  public hasRole(role: string): boolean {
    return this.getRoles().includes(role);
  }

  public isAdmin(): boolean {
    return this.hasRole('ADMIN') || this.hasRole('ADMINISTRATEUR');
  }

  public isRH(): boolean {
    return this.hasRole('RH');
  }

  public isManager(): boolean {
    return this.hasRole('MANAGER');
  }

  public getUsername(): string {
    const token = this.getToken();
    if (!token) return '';
    const decoded = this.decodeJwt(token);
    return decoded?.preferred_username || decoded?.sub || '';
  }

  public getEmail(): string {
    const token = this.getToken();
    if (!token) return '';
    const decoded = this.decodeJwt(token);
    return decoded?.email || '';
  }

  public getFirstName(): string {
    const token = this.getToken();
    if (!token) return '';
    const decoded = this.decodeJwt(token);
    return decoded?.given_name || '';
  }

  public getLastName(): string {
    const token = this.getToken();
    if (!token) return '';
    const decoded = this.decodeJwt(token);
    return decoded?.family_name || '';
  }

  public getFullName(): string {
    const first = this.getFirstName();
    const last = this.getLastName();
    if (first || last) return `${first} ${last}`.trim();
    return this.getUsername();
  }

  public initializeUserSession(): void {
    if (!this.isLoggedIn()) {
      this.clearSession();
      return;
    }

    this.http.get<any>(`${BACKEND_URL}/me`).pipe(
      map(user => {
        const email = user.email || this.getEmail() || 'user@corp.com';
        const name = user.name || (user.firstName && user.lastName ? `${user.firstName} ${user.lastName}` : '') || this.getFullName() || 'Utilisateur';

        let role: UserSession['role'] = 'Employé';
        const rawRole = (user.role || '').toUpperCase();
        if (rawRole === 'ADMIN' || rawRole === 'ADMINISTRATEUR') role = 'Administrateur';
        else if (rawRole === 'RH') role = 'RH';
        else if (rawRole === 'MANAGER') role = 'Manager';
        else {
          if (this.isAdmin()) role = 'Administrateur';
          else if (this.isRH()) role = 'RH';
          else if (this.isManager()) role = 'Manager';
        }

        let userPhoto = user.photoUrl || user.photo;
        if (userPhoto && userPhoto.startsWith('/')) {
          userPhoto = `http://localhost:8087${userPhoto}`;
        }
        const session: UserSession = {
          email,
          name,
          role,
          photo: userPhoto || 'assets/images/avatar.png',
          id: user.id,
          firstName: user.firstName,
          lastName: user.lastName,
          position: user.position,
          department: user.department,
          hireDate: user.hireDate,
          availableLeaveDays: user.availableLeaveDays,
          address: user.address,
          phone: user.phone,
          salary: user.salary
        };

        localStorage.setItem('hr_session', JSON.stringify(session));
        this.currentUserSubject.next(session);
      }),
      catchError(err => {
        console.warn('Fallback to token info for user session:', err);
        const email = this.getEmail() || 'user@corp.com';
        const name = this.getFullName() || 'Utilisateur';

        let role: UserSession['role'] = 'Employé';
        if (this.isAdmin()) role = 'Administrateur';
        else if (this.isRH()) role = 'RH';
        else if (this.isManager()) role = 'Manager';

        const session: UserSession = {
          email, name, role,
          photo: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150',
          firstName: this.getFirstName(),
          lastName: this.getLastName()
        };
        localStorage.setItem('hr_session', JSON.stringify(session));
        this.currentUserSubject.next(session);
        return of(null);
      })
    ).subscribe();

    this.scheduleTokenRefresh();
  }

  public forgotPassword(email: string): Observable<boolean> {
    return new Observable<boolean>(subscriber => {
      setTimeout(() => { subscriber.next(true); subscriber.complete(); }, 800);
    });
  }

  public tryRestoreSession(): Promise<boolean> {
    if (this.isLoggedIn()) {
      this.initializeUserSession();
      return Promise.resolve(true);
    }
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) return Promise.resolve(false);

    return new Promise(resolve => {
      this.refreshToken().subscribe(success => {
        if (success) {
          this.initializeUserSession();
          resolve(true);
        } else {
          resolve(false);
        }
      });
    });
  }

  private scheduleTokenRefresh(): void {
    clearTimeout(this.refreshTimer);
    const token = this.getToken();
    if (!token) return;
    const decoded = this.decodeJwt(token);
    const exp = decoded?.exp;
    if (!exp) return;

    const delay = exp * 1000 - Date.now() - 30000;
    if (delay > 0) {
      this.refreshTimer = setTimeout(() => {
        this.refreshToken().subscribe();
      }, delay);
    }
  }

  public toggleTheme(): void {
    this.setTheme(this.themeSubject.value === 'light' ? 'dark' : 'light');
  }

  public setTheme(theme: 'light' | 'dark'): void {
    this.themeSubject.next(theme);
    localStorage.setItem('hr_theme', theme);
    localStorage.setItem('theme', theme);
    document.body.setAttribute('data-theme', theme);
    if (theme === 'dark') {
      document.body.classList.add('dark-theme');
    } else {
      document.body.classList.remove('dark-theme');
    }
  }
}
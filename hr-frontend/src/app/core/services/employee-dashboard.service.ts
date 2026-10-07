import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { finalize, shareReplay, tap } from 'rxjs/operators';
import { EmployeeDashboard } from '../models/interfaces';

/**
 * Tableau de bord employé avec cache de session :
 * les dernières données s'affichent instantanément, puis sont rafraîchies en arrière-plan.
 */
@Injectable({ providedIn: 'root' })
export class EmployeeDashboardService {
  private readonly apiUrl = 'http://localhost:8087/api/employee/dashboard';
  private static readonly CACHE_KEY = 'employee_dashboard_cache';

  /** Dernières données connues (null si jamais chargées pendant cette session). */
  private readonly dataSubject = new BehaviorSubject<EmployeeDashboard | null>(this.readCache());
  readonly data$ = this.dataSubject.asObservable();

  /** Requête en cours, partagée pour ne jamais lancer deux fois le même appel. */
  private inFlight$: Observable<EmployeeDashboard> | null = null;

  constructor(private http: HttpClient) {}

  /** Charge (ou recharge) les données depuis le backend et met à jour le cache. */
  refresh(): Observable<EmployeeDashboard> {
    if (!this.inFlight$) {
      this.inFlight$ = this.http.get<EmployeeDashboard>(this.apiUrl).pipe(
        tap(data => {
          this.dataSubject.next(data);
          try {
            sessionStorage.setItem(EmployeeDashboardService.CACHE_KEY, JSON.stringify(data));
          } catch { /* stockage indisponible : on garde seulement la mémoire */ }
        }),
        finalize(() => this.inFlight$ = null),
        shareReplay(1)
      );
    }
    return this.inFlight$;
  }

  /** Démarre le chargement sans attendre la réponse (appelé juste après la connexion). */
  prefetch(): void {
    this.refresh().subscribe({ error: () => { /* la page réessaiera à son ouverture */ } });
  }

  /** À appeler à la déconnexion : les données d'un employé ne doivent jamais rester pour le suivant. */
  clear(): void {
    this.dataSubject.next(null);
    sessionStorage.removeItem(EmployeeDashboardService.CACHE_KEY);
  }

  private readCache(): EmployeeDashboard | null {
    try {
      const raw = sessionStorage.getItem(EmployeeDashboardService.CACHE_KEY);
      return raw ? JSON.parse(raw) as EmployeeDashboard : null;
    } catch {
      return null;
    }
  }
}
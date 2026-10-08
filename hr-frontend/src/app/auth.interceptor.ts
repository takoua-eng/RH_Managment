import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, finalize, shareReplay, switchMap, throwError } from 'rxjs';
import { AuthService } from './core/services/auth.service';
import { environment } from '../environments/environment';

/**
 * Rafraîchissement en cours, partagé entre toutes les requêtes :
 * si plusieurs requêtes reçoivent un 401 en même temps, un seul rafraîchissement est lancé.
 */
let refreshInFlight$: Observable<boolean> | null = null;

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();

  const isApiRequest = req.url.startsWith(environment.apiUrl) || req.url.includes('/api/');
  const isAuthEndpoint =
    req.url.includes('/auth/login') ||
    req.url.includes('/auth/refresh') ||
    req.url.includes('/auth/logout');

  // Ajoute le jeton aux appels de l'API (sauf aux points d'entrée d'authentification)
  const authReq = token && isApiRequest && !isAuthEndpoint
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // On ne tente un rafraîchissement que pour un 401 sur une requête partie AVEC un jeton.
      // Une requête partie sans jeton n'a rien à rafraîchir : on ne déconnecte personne.
      if (error.status !== 401 || isAuthEndpoint || !token) {
        return throwError(() => error);
      }

      // Un seul rafraîchissement à la fois, partagé par toutes les requêtes en attente
      if (!refreshInFlight$) {
        refreshInFlight$ = authService.refreshToken().pipe(
          finalize(() => (refreshInFlight$ = null)),
          shareReplay(1)
        );
      }

      return refreshInFlight$.pipe(
        switchMap(refreshed => {
          if (!refreshed) {
            // Session réellement expirée : retour à la page de connexion
            authService.logout();
            return throwError(() => error);
          }
          // Nouvelle tentative, une seule fois, avec le nouveau jeton.
          // Si elle échoue encore (adresse incorrecte, droits insuffisants…), l'erreur
          // est simplement renvoyée, sans nouvelle tentative ni déconnexion.
          return next(req.clone({
            setHeaders: { Authorization: `Bearer ${authService.getToken()}` }
          }));
        })
      );
    })
  );
};
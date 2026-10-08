import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from './core/services/auth.service';
import { catchError, switchMap, throwError } from 'rxjs';
import { environment } from '../environments/environment';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();

  // Attach token if present and request is directed to backend
  let authReq = req;
  if (token && (req.url.includes('/api/') || req.url.startsWith(`${environment.apiUrl}`))) {
    authReq = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    });
  }

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // If 401 Unauthorized and not already an auth endpoint request, attempt token refresh
      if (
        error.status === 401 &&
        !req.url.includes('/api/auth/login') &&
        !req.url.includes('/api/auth/refresh')
      ) {
        return authService.refreshToken().pipe(
          switchMap(refreshed => {
            if (refreshed) {
              const newToken = authService.getToken();
              const retriedReq = req.clone({
                setHeaders: { Authorization: `Bearer ${newToken}` }
              });
              return next(retriedReq);
            } else {
              authService.logout();
              return throwError(() => error);
            }
          }),
          catchError(refreshErr => {
            authService.logout();
            return throwError(() => refreshErr);
          })
        );
      }
      return throwError(() => error);
    })
  );
};
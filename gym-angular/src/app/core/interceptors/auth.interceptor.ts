import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

/** Rotas de autenticação: entram sem identidade anterior. */
const AUTH_PATHS = ['/api/v1/auth/login', '/api/v1/auth/signup', '/api/v1/auth/bootstrap'];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const isAuthRequest = AUTH_PATHS.some(p => req.url.startsWith(p));

  let headers = req.headers;

  // O token da sessão anterior NÃO vai junto no login: o servidor autenticaria
  // o usuário antigo e recusaria a entrada na academia nova, prendendo quem
  // quer trocar de conta.
  const token = auth.token();
  if (token && !isAuthRequest) {
    headers = headers.set('Authorization', `Bearer ${token}`);
  }

  // A academia vai também no login — é ela que define o papel dentro do token.
  const tenant = auth.tenant();
  if (tenant) {
    headers = headers.set('X-Tenant-ID', tenant);
  }

  return next(req.clone({ headers }));
};

import { HttpInterceptorFn } from '@angular/common/http';
import { inject }            from '@angular/core';
import { AuthService }       from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth   = inject(AuthService);
  const token  = auth.token();
  const tenant = auth.tenant();

  let headers = req.headers;
  if (token)  headers = headers.set('Authorization', `Bearer ${token}`);
  if (tenant) headers = headers.set('X-Tenant-ID', tenant);

  return next(req.clone({ headers }));
};

import { Injectable, signal, computed } from '@angular/core';
import { HttpClient }   from '@angular/common/http';
import { Router }       from '@angular/router';
import { tap }          from 'rxjs/operators';
import { AuthResponse, LoginRequest, SignupRequest, JwtPayload } from '../models';

const API = '/api/v1';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly TOKEN_KEY  = 'gym_token';
  private readonly TENANT_KEY = 'gym_tenant';

  readonly token   = signal<string | null>(this._loadToken());
  readonly tenant  = signal<string | null>(localStorage.getItem(this.TENANT_KEY));

  readonly isLoggedIn  = computed(() => !!this.token());
  readonly currentUser = computed(() => this._decode(this.token()));
  readonly tenantRole  = computed(() => this.currentUser()?.tenantRole ?? null);
  readonly isAdmin     = computed(() =>
    ['ADMIN_APP', 'ADMIN_WEB'].includes(this.currentUser()?.role ?? '') ||
    ['OWNER','MANAGER'].includes(this.tenantRole() ?? '')
  );

  constructor(private http: HttpClient, private router: Router) {}

  setTenant(slug: string) {
    localStorage.setItem(this.TENANT_KEY, slug);
    this.tenant.set(slug);
  }

  login(req: LoginRequest) {
    return this.http.post<AuthResponse>(`${API}/auth/login`, req).pipe(
        tap(res => { localStorage.setItem(this.TOKEN_KEY, res.accessToken); this.token.set(res.accessToken); })
    );
  }

  signup(req: SignupRequest) {
    return this.http.post<AuthResponse>(`${API}/auth/signup`, req).pipe(
        tap(res => { localStorage.setItem(this.TOKEN_KEY, res.accessToken); this.token.set(res.accessToken); })
    );
  }

  logout() {
    localStorage.removeItem(this.TOKEN_KEY);
    this.token.set(null);
    this.router.navigate(['/login']);
  }

  private _loadToken(): string | null {
    const t = localStorage.getItem(this.TOKEN_KEY);
    if (!t) return null;
    const payload = this._decode(t);
    if (!payload || payload.exp * 1000 < Date.now()) {
      localStorage.removeItem(this.TOKEN_KEY);
      return null;
    }
    return t;
  }

  private _decode(token: string | null): JwtPayload | null {
    if (!token) return null;
    try {
      const b64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      return JSON.parse(atob(b64)) as JwtPayload;
    } catch { return null; }
  }
}

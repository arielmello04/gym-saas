import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import {
  AdminSubscriptionItem, AvailabilityItem, BookingResponse, BookingScope,
  CheckinItem, CheckinProviderSummary, CreatePlanRequest, MeResponse, MyBookingItem,
  PartnerHealth, PartnerLink, PartnerTenantConfig, PartnerWebhookUrl, PendingPartnerCheckin,
  PaymentItem, Plan, ProfilePreferences, StartCheckinRequest, StartCheckinResponse,
  SubscribeRequest, Subscription, TenantInfo, TenantMember, UpdatePlanRequest,
  UpsertPartnerConfigRequest, UpsertPartnerLinkRequest, WaitlistEntry,
} from '../models';

const API = '/api/v1';

/**
 * Cada caminho abaixo corresponde a um endpoint que existe de fato.
 * Sete deles apontavam para rotas inexistentes (/bookings/me, /plans,
 * /payments/my-subscription, /checkin/me, entre outras) — as telas
 * simplesmente ficavam vazias, sem erro visível.
 */

@Injectable({ providedIn: 'root' })
export class MeApiService {
  constructor(private http: HttpClient) {}
  get() { return this.http.get<MeResponse>(`${API}/me`); }
}

@Injectable({ providedIn: 'root' })
export class BookingApiService {
  constructor(private http: HttpClient) {}

  getAvailability(from: string, to: string) {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<AvailabilityItem[]>(`${API}/classes/availability`, { params });
  }

  book(sessionId: number) {
    return this.http.post<BookingResponse>(`${API}/classes/${sessionId}/book`, {});
  }

  myBookings(scope: BookingScope = 'upcoming') {
    const params = new HttpParams().set('scope', scope);
    return this.http.get<MyBookingItem[]>(`${API}/my/bookings`, { params });
  }

  cancel(bookingId: number) {
    return this.http.delete<void>(`${API}/bookings/${bookingId}`);
  }
}

@Injectable({ providedIn: 'root' })
export class WaitlistApiService {
  constructor(private http: HttpClient) {}
  join(sessionId: number)    { return this.http.post<WaitlistEntry>(`${API}/waitlist/${sessionId}/join`, {}); }
  confirm(sessionId: number) { return this.http.post<BookingResponse>(`${API}/waitlist/${sessionId}/confirm`, {}); }
  leave(sessionId: number)   { return this.http.delete<void>(`${API}/waitlist/${sessionId}/leave`); }
  myEntries()                { return this.http.get<WaitlistEntry[]>(`${API}/waitlist/me`); }
}

@Injectable({ providedIn: 'root' })
export class PlansApiService {
  constructor(private http: HttpClient) {}
  list()    { return this.http.get<Plan[]>(`${API}/plans`); }
  listAll() { return this.http.get<Plan[]>(`${API}/admin/plans`); }
  create(req: CreatePlanRequest)            { return this.http.post<Plan>(`${API}/admin/plans`, req); }
  update(id: number, req: UpdatePlanRequest) { return this.http.put<Plan>(`${API}/admin/plans/${id}`, req); }
  deactivate(id: number)                    { return this.http.delete<void>(`${API}/admin/plans/${id}`); }
}

@Injectable({ providedIn: 'root' })
export class PaymentApiService {
  constructor(private http: HttpClient) {}
  subscribe(req: SubscribeRequest) { return this.http.post<Subscription>(`${API}/payments/subscribe`, req); }
  mySubscription()                 { return this.http.get<Subscription>(`${API}/payments/subscription`); }
  myInvoices()                     { return this.http.get<PaymentItem[]>(`${API}/payments/invoices`); }
  cancel()                         { return this.http.delete<void>(`${API}/payments/subscription`); }
  adminSubscriptions()             { return this.http.get<AdminSubscriptionItem[]>(`${API}/admin/subscriptions`); }
}

@Injectable({ providedIn: 'root' })
export class CheckinApiService {
  constructor(private http: HttpClient) {}

  start(req: StartCheckinRequest) {
    return this.http.post<StartCheckinResponse>(`${API}/checkin/start`, req);
  }

  myHistory() { return this.http.get<CheckinItem[]>(`${API}/checkin/history`); }

  /** Conciliação com Wellhub e TotalPass. */
  adminSummary(from: string, to: string) {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<CheckinProviderSummary[]>(`${API}/admin/checkins/summary`, { params });
  }
}

/**
 * Operação dos parceiros de check-in.
 *
 * Wellhub e TotalPass identificam o aluno por um id do lado deles — o vínculo é
 * o que liga esse id ao cadastro da academia. E a fila existe porque a TotalPass
 * empurra as entradas por webhook, em vez de nós consultarmos.
 */
@Injectable({ providedIn: 'root' })
export class PartnerApiService {
  constructor(private http: HttpClient) {}

  links()                              { return this.http.get<PartnerLink[]>(`${API}/admin/partners/links`); }
  upsertLink(req: UpsertPartnerLinkRequest) { return this.http.put<PartnerLink>(`${API}/admin/partners/links`, req); }
  removeLink(id: number)               { return this.http.delete<void>(`${API}/admin/partners/links/${id}`); }

  pending() { return this.http.get<PendingPartnerCheckin[]>(`${API}/admin/partners/checkins/pending`); }

  /** `email` só é necessário na primeira visita, para criar o vínculo. */
  confirm(id: number, email?: string) {
    const params = email ? new HttpParams().set('email', email) : undefined;
    return this.http.post<PendingPartnerCheckin>(
      `${API}/admin/partners/checkins/${id}/confirm`, {}, { params });
  }

  /** Credenciais desta academia (Gym ID e place_api_key). */
  configs() { return this.http.get<PartnerTenantConfig[]>(`${API}/admin/partners/config`); }
  upsertConfig(req: UpsertPartnerConfigRequest) {
    return this.http.put<PartnerTenantConfig>(`${API}/admin/partners/config`, req);
  }

  /** Testa credencial e alcance dos dois parceiros, sem liberar entrada nenhuma. */
  diagnostics() { return this.http.get<PartnerHealth[]>(`${API}/admin/partners/diagnostics`); }

  webhookUrl()      { return this.http.get<PartnerWebhookUrl>(`${API}/admin/partners/webhook-url`); }
  registerWebhook() { return this.http.post<{ url: string; registered: boolean }>(
                        `${API}/admin/partners/webhook-url/register`, {}); }
}

@Injectable({ providedIn: 'root' })
export class ProfileApiService {
  constructor(private http: HttpClient) {}
  get()                             { return this.http.get<ProfilePreferences>(`${API}/profile/preferences`); }
  update(prefs: ProfilePreferences) { return this.http.put<ProfilePreferences>(`${API}/profile/preferences`, prefs); }
}

@Injectable({ providedIn: 'root' })
export class TenantApiService {
  constructor(private http: HttpClient) {}
  get()                                  { return this.http.get<TenantInfo>(`${API}/tenant`); }
  members()                              { return this.http.get<TenantMember[]>(`${API}/tenant/members`); }
  addMember(email: string, role: string) { return this.http.post<TenantMember>(`${API}/tenant/members`, { email, role }); }
  removeMember(userId: number)           { return this.http.delete<void>(`${API}/tenant/members/${userId}`); }
}

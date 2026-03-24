import { Injectable }   from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import {
  AvailabilityItem, BookingResponse, MyBookingItem, WaitlistEntry,
  Plan, SubscribeRequest, SubscribeResponse, Subscription, PaymentItem,
  CheckinStatus, ProfilePreferences, TenantInfo, TenantMember
} from '../models';

const API = '/api/v1';

@Injectable({ providedIn: 'root' })
export class BookingApiService {
  constructor(private http: HttpClient) {}
  getAvailability(from: string, to: string) {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<AvailabilityItem[]>(`${API}/classes/availability`, { params });
  }
  book(sessionId: number) { return this.http.post<BookingResponse>(`${API}/bookings`, { sessionId }); }
  myBookings()            { return this.http.get<MyBookingItem[]>(`${API}/bookings/me`); }
  cancel(bookingId: number) { return this.http.delete<void>(`${API}/bookings/${bookingId}`); }
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
  list() { return this.http.get<Plan[]>(`${API}/plans`); }
}

@Injectable({ providedIn: 'root' })
export class PaymentApiService {
  constructor(private http: HttpClient) {}
  subscribe(req: SubscribeRequest) { return this.http.post<SubscribeResponse>(`${API}/payments/subscribe`, req); }
  mySubscription()                 { return this.http.get<Subscription>(`${API}/payments/my-subscription`); }
  myInvoices()                     { return this.http.get<PaymentItem[]>(`${API}/payments/invoices`); }
  cancel()                         { return this.http.delete<void>(`${API}/payments/cancel`); }
}

@Injectable({ providedIn: 'root' })
export class CheckinApiService {
  constructor(private http: HttpClient) {}
  start(provider: string)  { return this.http.post<CheckinStatus>(`${API}/checkin/start`, { provider }); }
  myHistory()              { return this.http.get<CheckinStatus[]>(`${API}/checkin/me`); }
}

@Injectable({ providedIn: 'root' })
export class ProfileApiService {
  constructor(private http: HttpClient) {}
  get()                          { return this.http.get<ProfilePreferences>(`${API}/profile/preferences`); }
  update(prefs: ProfilePreferences) { return this.http.put<ProfilePreferences>(`${API}/profile/preferences`, prefs); }
}

@Injectable({ providedIn: 'root' })
export class TenantApiService {
  constructor(private http: HttpClient) {}
  get()                              { return this.http.get<TenantInfo>(`${API}/tenant`); }
  members()                          { return this.http.get<TenantMember[]>(`${API}/tenant/members`); }
  addMember(email: string, role: string) { return this.http.post<TenantMember>(`${API}/tenant/members`, { email, role }); }
  removeMember(userId: number)       { return this.http.delete<void>(`${API}/tenant/members/${userId}`); }
}

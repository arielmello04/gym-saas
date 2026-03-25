// ── Auth ─────────────────────────────────────────────────────
export interface LoginRequest  { email: string; password: string; }
export interface SignupRequest { email: string; password: string; inviteToken: string; }
export interface AuthResponse  { accessToken: string; tokenType: string; }

// ── Tenant ───────────────────────────────────────────────────
export interface TenantInfo {
  id: number; slug: string; name: string;
  plan: string; active: boolean;
}
export interface TenantMember {
  userId: number; email: string; role: string; active: boolean; joinedAt: string;
}

// ── Plans ────────────────────────────────────────────────────
export interface Plan {
  id: number; name: string; description: string;
  priceCents: number; priceReais: number; currency: string;
  intervalMonths: number; intervalLabel: string; active: boolean;
}

// ── Booking ──────────────────────────────────────────────────
export interface AvailabilityItem {
  sessionId: number; classTypeCode: string; classTypeName: string;
  startAt: string; endAt: string;
  capacity: number; spotsLeft: number; notes?: string;
}
export interface BookingResponse {
  id: number; sessionId: number; status: string;
  createdAt: string; canceledAt?: string;
}
export interface MyBookingItem {
  bookingId: number; sessionId: number;
  classTypeName: string; startAt: string; endAt: string; status: string;
}

// ── Waitlist ─────────────────────────────────────────────────
export interface WaitlistEntry {
  entryId: number; sessionId: number;
  sessionType: string; sessionStartAt: string;
  position: number; status: string;
  notifiedAt?: string; expiresAt?: string;
  totalWaiting: number;
}

// ── Payments ─────────────────────────────────────────────────
export interface AdminSubscriptionSummary {
  id: number; userEmail: string; planName: string; priceCents: number; currency: string;
  status: string; currentPeriodEnd: string; nextBillingAt: string;
}
export interface SubscribeRequest {
  planName: string; priceCents: number; currency: string;
  paymentMethod: 'pix' | 'boleto' | 'credit_card';
  // Cartão
  cardToken?: string; cardPaymentMethodId?: string;
  cardIssuerId?: string; installments?: number;
  // Boleto
  customerDocument?: string; customerZipCode?: string;
  customerStreet?: string; customerStreetNum?: string;
  customerNeighborhood?: string; customerCity?: string; customerState?: string;
}
export interface SubscribeResponse {
  subscriptionId: number; subscriptionStatus: string;
  paymentId: number; paymentStatus: string;
  providerRef: string;
  pixCode?: string; pixQrCode?: string;
  boletoBarcode?: string; boletoUrl?: string;
  actionUrl?: string; expiresAt?: string;
}
export interface Subscription {
  id: number; planName: string; priceCents: number; currency: string;
  billingDay: number; status: string;
  currentPeriodStart: string; currentPeriodEnd: string; nextBillingAt: string;
}
export interface PaymentItem {
  id: number; amountCents: number; currency: string; status: string;
  providerRef?: string; dueAt: string; paidAt?: string; createdAt: string;
}

// ── Checkin ──────────────────────────────────────────────────
export interface CheckinStatus {
  checkinId: number; status: string; provider: string;
  startedAt: string; completedAt?: string;
}

// ── Profile ──────────────────────────────────────────────────
export interface ProfilePreferences {
  allowRecording: boolean; allowPhotos: boolean; allowFaceVisibility: boolean;
}

// ── Token JWT decoded ────────────────────────────────────────
export interface JwtPayload {
  sub: string; role: string;
  tenantSlug?: string; tenantRole?: string;
  exp: number;
}

/**
 * Tipos da API.
 *
 * Espelham o contrato real de /v3/api-docs. Vários deles estavam descritos
 * aqui de um jeito que o backend nunca devolveu — o que só aparecia como tela
 * vazia, porque TypeScript não confere resposta de rede.
 */

// ── Auth ─────────────────────────────────────────────────────
export interface LoginRequest  { email: string; password: string; }
export interface SignupRequest { email: string; password: string; inviteToken: string; }
export interface AuthResponse  { accessToken: string; tokenType: string; }

export interface MeResponse {
  email: string;
  role: string;
  hasSubscription: boolean;
  subscriptionStatus: string | null;
}

// ── Tenant ───────────────────────────────────────────────────
export interface TenantInfo   { id: number; slug: string; name: string; plan: string; active: boolean; }
export interface TenantMember { userId: number; email: string; role: string; active: boolean; joinedAt: string; }

// ── Planos ───────────────────────────────────────────────────
export interface Plan {
  id: number;
  code: string;
  name: string;
  description: string | null;
  priceCents: number;
  /** Já vem calculado do servidor; não dividir por 100 na tela. */
  priceReais: number;
  currency: string;
  intervalMonths: number;
  /** "mes" | "trimestre" | "ano" | "N meses" */
  intervalLabel: string;
  active: boolean;
}

export interface CreatePlanRequest {
  code: string;
  name: string;
  description?: string;
  priceCents: number;
  intervalMonths: number;
  currency?: string;
  active?: boolean;
  sortOrder?: number;
}

export interface UpdatePlanRequest {
  name?: string;
  description?: string;
  priceCents?: number;
  intervalMonths?: number;
  active?: boolean;
  sortOrder?: number;
}

// ── Agenda e reservas ────────────────────────────────────────
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
  classTypeCode: string; classTypeName: string;
  startAt: string; endAt: string;
  status: string;
  /** Falso quando a aula já entrou na janela de corte de cancelamento. */
  cancellable: boolean;
}

export type BookingScope = 'upcoming' | 'past' | 'all';

// ── Fila de espera ───────────────────────────────────────────
export interface WaitlistEntry {
  entryId: number; sessionId: number;
  sessionType: string; sessionStartAt: string;
  position: number; status: string;
  notifiedAt?: string; expiresAt?: string;
  totalWaiting: number;
}

// ── Pagamentos ───────────────────────────────────────────────
export interface SubscribeRequest {
  /** Só o plano: preço e nome vêm do catálogo do servidor. */
  planId: number;
  paymentMethod: 'pix' | 'boleto' | 'credit_card';
  cardToken?: string; cardPaymentMethodId?: string;
  cardIssuerId?: string; installments?: number;
  customerDocument?: string; customerZipCode?: string;
  customerStreet?: string; customerStreetNum?: string;
  customerNeighborhood?: string; customerCity?: string; customerState?: string;
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

export interface AdminSubscriptionItem {
  id: number; userEmail: string; planName: string;
  priceCents: number; priceReais: number; currency: string;
  status: string; billingDay: number;
  currentPeriodEnd: string; nextBillingAt: string; canceledAt?: string;
}

// ── Check-in ─────────────────────────────────────────────────
export type CheckinProvider = 'WELLHUB' | 'TOTALPASS' | 'DIRECT';

export interface StartCheckinRequest {
  /** TOTALPASS não entra aqui: a entrada dele chega por webhook. */
  provider: CheckinProvider;
  /**
   * Wellhub ID do aluno. Normalmente vem do vínculo cadastrado; só se informa
   * aqui na primeira visita, quando o vínculo ainda não existe.
   */
  code?: string;
  /** Só para DIRECT. */
  gymName?: string;
}

// ── Parceiros: vínculo e fila de entradas ────────────────────
export interface PartnerLink {
  id: number;
  provider: string;
  email: string;
  /** Wellhub: gympass_id de 13 dígitos. TotalPass: código do usuário. */
  externalId: string;
  customCode: string | null;
  /** CPF, como a TotalPass manda no webhook. */
  document: string | null;
  updatedAt: string;
}

export interface UpsertPartnerLinkRequest {
  provider: 'WELLHUB' | 'TOTALPASS';
  email: string;
  externalId: string;
  customCode?: string;
  document?: string;
}

export interface PendingPartnerCheckin {
  id: number;
  provider: string;
  userName: string | null;
  /** Nulo quando o CPF ainda não está vinculado a um cadastro nosso. */
  userEmail: string | null;
  document: string | null;
  planCode: string | null;
  status: string;
  failureReason: string | null;
  startedAt: string | null;
  /** A TotalPass dá 90 minutos para confirmar. */
  expiresAt: string | null;
}

export interface PartnerWebhookUrl {
  url: string;
  secretConfigured: boolean;
}

/** Credencial que é DESTA academia (o resto é da integradora, em variável de ambiente). */
export interface PartnerTenantConfig {
  id: number;
  provider: string;
  /** Wellhub: header X-Gym-Id desta unidade. */
  gymId: string | null;
  /** TotalPass: place_api_key mascarada — o servidor nunca devolve inteira. */
  placeApiKeyMasked: string | null;
  active: boolean;
  updatedAt: string;
}

export interface UpsertPartnerConfigRequest {
  provider: 'WELLHUB' | 'TOTALPASS';
  gymId?: string;
  placeApiKey?: string;
  active?: boolean;
}

/** Resultado do teste de conexão com o parceiro. */
export interface PartnerHealth {
  provider: string;
  mode: string;
  configured: boolean;
  reachable: boolean;
  credentialsValid: boolean;
  detail: string;
}

export interface StartCheckinResponse {
  checkinId: number;
  provider: string;
  /** COMPLETED | FAILED */
  status: string;
  approved: boolean;
  memberName: string | null;
  /** Motivo da recusa; nulo quando aprovado. */
  message: string | null;
}

export interface CheckinItem {
  id: number;
  provider: string;
  gymName: string | null;
  providerRef: string;
  status: string;
  partnerPlan: string | null;
  failureReason: string | null;
  startedAt: string;
  completedAt: string | null;
}

export interface CheckinProviderSummary {
  provider: string;
  completed: number;
  failed: number;
  pending: number;
  total: number;
}

// ── Perfil ───────────────────────────────────────────────────
export interface ProfilePreferences {
  allowRecording: boolean; allowPhotos: boolean; allowFaceVisibility: boolean;
}

// ── JWT ──────────────────────────────────────────────────────
export interface JwtPayload {
  sub: string; role: string;
  tenantSlug?: string; tenantRole?: string;
  exp: number;
}

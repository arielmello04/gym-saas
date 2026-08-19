package com.gymsystem.payments;

import com.gymsystem.booking.BookingEnforcer;
import com.gymsystem.notifications.EmailService;
import com.gymsystem.payments.dto.*;
import com.gymsystem.payments.gateway.*;
import com.gymsystem.payments.plan.MembershipPlanService;
import com.gymsystem.tenant.TenantRepository;
import com.gymsystem.tenant.context.TenantGuard;
import com.gymsystem.user.User;
import com.gymsystem.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository      paymentRepository;
    private final UserRepository         userRepository;
    private final TenantRepository       tenantRepository;
    private final BookingEnforcer        bookingEnforcer;
    private final EmailService           emailService;
    private final PaymentGateway         paymentGateway;   // injected via @ConditionalOnProperty
    private final MembershipPlanService  planService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // ── Subscribe ─────────────────────────────────────────────

    /**
     * Creates a subscription and immediately charges the first invoice
     * through the configured payment gateway.
     */
    @Transactional
    public SubscriptionResponse subscribe(SubscribeRequest req) {
        Long tenantId = TenantGuard.currentTenantId();
        var  tenant   = tenantRepository.findById(tenantId).orElseThrow();
        User user     = getCurrentUser();

        // Prevent duplicate active subscription
        var existing = subscriptionRepository.findByUserIdAndTenantIdAndStatusIn(
                user.getId(), tenantId,
                Set.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE));
        if (existing.isPresent())
            throw new IllegalStateException("User already has an active subscription in this tenant");

        Instant now        = Instant.now();
        int     billingDay = getSafeBillingDay(user.getCreatedAt());
        var     period     = computeMonthlyPeriod(now, billingDay);

        // Preco vem do catalogo da academia. O cliente escolhe o plano; quanto
        // custa e com o servidor.
        var plan = planService.requireActiveForSubscription(req.getPlanId());

        Subscription sub = Subscription.builder()
                .user(user).tenant(tenant)
                .plan(plan)
                .planName(plan.getName())
                .priceCents(plan.getPriceCents())
                .currency(plan.getCurrency())
                .billingDay(billingDay)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(period.start())
                .currentPeriodEnd(period.end())
                .nextBillingAt(period.end())
                .createdAt(now)
                .build();
        sub = subscriptionRepository.save(sub);

        // Charge first invoice via gateway
        Payment payment = chargeViaGateway(sub, req.getPaymentMethod(), now, period.end());
        paymentRepository.save(payment);

        return toResponse(sub);
    }

    // ── Queries ───────────────────────────────────────────────

    public SubscriptionResponse getMySubscription() {
        Long tenantId = TenantGuard.currentTenantId();
        var  sub      = findActiveSubForCurrentUser(tenantId);
        return toResponse(sub);
    }

    public List<PaymentItem> listMyInvoices() {
        Long tenantId = TenantGuard.currentTenantId();
        var  sub      = findActiveSubForCurrentUser(tenantId);
        return paymentRepository.findBySubscriptionIdOrderByCreatedAtDesc(sub.getId())
                .stream().map(this::toPaymentItem).toList();
    }

    @Transactional
    public void cancelMySubscription() {
        Long tenantId = TenantGuard.currentTenantId();
        var  sub      = findActiveSubForCurrentUser(tenantId);
        sub.setStatus(SubscriptionStatus.CANCELED);
        sub.setCanceledAt(Instant.now());
        subscriptionRepository.save(sub);
        log.info("[Subscription] Canceled sub={} for user={}", sub.getId(), sub.getUser().getEmail());
    }

    // ── Webhook from gateway ──────────────────────────────────

    /**
     * Handles a verified webhook event from the payment provider.
     * Called by PaymentCallbackController after signature verification.
     */
    @Transactional
    public void handleGatewayEvent(String providerRef, GatewayPaymentStatus gatewayStatus) {
        Payment p = paymentRepository.findByProviderRef(providerRef)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + providerRef));

        if (p.getStatus() != PaymentStatus.PENDING) return; // idempotent

        switch (gatewayStatus) {
            case PAID -> {
                p.setStatus(PaymentStatus.PAID);
                p.setPaidAt(Instant.now());
                paymentRepository.save(p);
                rollSubscription(p.getSubscription());
                emailService.sendPaymentReminder(p.getSubscription().getUser(),
                        "Pagamento confirmado",
                        "Seu pagamento foi confirmado! Sua assinatura está ativa.");
                log.info("[Subscription] Payment {} marked PAID", p.getId());
            }
            case FAILED, CANCELED -> {
                p.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(p);
                Subscription s = p.getSubscription();
                s.setStatus(SubscriptionStatus.PAST_DUE);
                subscriptionRepository.save(s);
                bookingEnforcer.enforcePastDue(s.getUser().getId(), s.getTenant().getId());
                emailService.sendPaymentReminder(s.getUser(),
                        "Pagamento falhou — Ação necessária",
                        "Não conseguimos processar seu pagamento. Suas reservas futuras foram suspensas.");
                log.warn("[Subscription] Payment {} FAILED — sub {} set to PAST_DUE", p.getId(), s.getId());
            }
            default -> log.debug("[Subscription] Ignoring gateway status {} for payment {}", gatewayStatus, p.getId());
        }
    }

    // ── Scheduler-triggered billing ──────────────────────────

    /**
     * Called by BillingScheduler for payments that are due.
     * Re-charges via gateway and handles the response.
     */
    @Transactional
    public void processPayment(Payment payment) {
        Subscription sub      = payment.getSubscription();
        Long         tenantId = sub.getTenant().getId();

        payment.setAttemptCount(payment.getAttemptCount() + 1);
        payment.setLastAttemptAt(Instant.now());
        paymentRepository.save(payment);

        try {
            GatewayChargeResponse resp = paymentGateway.charge(
                    GatewayChargeRequest.builder()
                            .idempotencyKey("retry-" + payment.getId() + "-" + payment.getAttemptCount())
                            .amountCents(payment.getAmountCents())
                            .currency(payment.getCurrency())
                            .customerEmail(sub.getUser().getEmail())
                            .customerName(sub.getUser().getEmail())
                            .description("GymSystem — " + sub.getPlanName())
                            .callbackUrl(baseUrl + "/api/v1/payments/callback/webhook")
                            .build()
            );

            payment.setProviderRef(resp.getProviderRef());

            if (resp.getStatus() == GatewayPaymentStatus.PAID) {
                payment.setStatus(PaymentStatus.PAID);
                payment.setPaidAt(Instant.now());
                paymentRepository.save(payment);
                rollSubscription(sub);
            } else {
                // PENDING = async method (Pix/Boleto) — wait for webhook
                payment.setStatus(PaymentStatus.PENDING);
                paymentRepository.save(payment);
            }

        } catch (Exception e) {
            log.error("[Subscription] Gateway charge error for payment {}: {}", payment.getId(), e.getMessage());
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            sub.setStatus(SubscriptionStatus.PAST_DUE);
            subscriptionRepository.save(sub);
            bookingEnforcer.enforcePastDue(sub.getUser().getId(), tenantId);
        }
    }

    // ── Private helpers ───────────────────────────────────────

    private Payment chargeViaGateway(Subscription sub, String paymentMethod, Instant now, Instant dueAt) {
        String idempotencyKey = "sub-" + sub.getId() + "-init";
        GatewayChargeResponse resp;
        PaymentStatus initialStatus;

        try {
            resp = paymentGateway.charge(
                    GatewayChargeRequest.builder()
                            .idempotencyKey(idempotencyKey)
                            .amountCents(sub.getPriceCents())
                            .currency(sub.getCurrency())
                            .customerEmail(sub.getUser().getEmail())
                            .customerName(sub.getUser().getEmail())
                            .description("GymSystem — " + sub.getPlanName())
                            .paymentMethod(paymentMethod)
                            .callbackUrl(baseUrl + "/api/v1/payments/callback/webhook")
                            .build()
            );
            initialStatus = resp.getStatus() == GatewayPaymentStatus.PAID
                    ? PaymentStatus.PAID : PaymentStatus.PENDING;
        } catch (Exception e) {
            log.error("[Subscription] Initial charge failed: {}", e.getMessage());
            resp = GatewayChargeResponse.builder()
                    .providerRef("FAILED-" + UUID.randomUUID())
                    .status(GatewayPaymentStatus.FAILED)
                    .build();
            initialStatus = PaymentStatus.FAILED;
            sub.setStatus(SubscriptionStatus.PAST_DUE);
            subscriptionRepository.save(sub);
        }

        return Payment.builder()
                .subscription(sub).tenant(sub.getTenant())
                .amountCents(sub.getPriceCents())
                .currency(sub.getCurrency())
                .status(initialStatus)
                .provider(gatewayName())
                .providerRef(resp.getProviderRef())
                .dueAt(dueAt)
                .paidAt(initialStatus == PaymentStatus.PAID ? Instant.now() : null)
                .createdAt(now)
                .attemptCount(1)
                .build();
    }

    private void rollSubscription(Subscription sub) {
        if (sub.getStatus() == SubscriptionStatus.CANCELED) return;
        var next = computeMonthlyPeriod(sub.getCurrentPeriodEnd(), sub.getBillingDay());
        sub.setCurrentPeriodStart(next.start());
        sub.setCurrentPeriodEnd(next.end());
        sub.setNextBillingAt(next.end());
        sub.setStatus(SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(sub);

        paymentRepository.save(Payment.builder()
                .subscription(sub).tenant(sub.getTenant())
                .amountCents(sub.getPriceCents()).currency(sub.getCurrency())
                .status(PaymentStatus.PENDING).provider(gatewayName())
                .dueAt(next.end()).createdAt(Instant.now()).attemptCount(0)
                .build());
    }

    private Subscription findActiveSubForCurrentUser(Long tenantId) {
        User user = getCurrentUser();
        return subscriptionRepository.findByUserIdAndTenantIdAndStatusIn(
                user.getId(), tenantId,
                Set.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE))
                .orElseThrow(() -> new IllegalStateException("Subscription not found"));
    }

    private record Period(Instant start, Instant end) {}

    private Period computeMonthlyPeriod(Instant anchor, int billingDay) {
        ZonedDateTime now = anchor.atZone(ZoneOffset.UTC);
        ZonedDateTime start, end;
        if (now.getDayOfMonth() < billingDay) {
            start = clampDay(now.minusMonths(1), billingDay);
            end   = clampDay(now, billingDay);
        } else {
            start = clampDay(now, billingDay);
            end   = clampDay(now.plusMonths(1), billingDay);
        }
        return new Period(start.toInstant(), end.toInstant());
    }

    private ZonedDateTime clampDay(ZonedDateTime base, int day) {
        return base.withDayOfMonth(Math.min(day, base.toLocalDate().lengthOfMonth()))
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
    }

    private int getSafeBillingDay(Instant createdAt) {
        return createdAt.atZone(ZoneOffset.UTC).getDayOfMonth();
    }

    private String gatewayName() {
        return paymentGateway.getClass().getSimpleName()
                .replace("Gateway", "").toUpperCase();
    }

    private SubscriptionResponse toResponse(Subscription s) {
        return new SubscriptionResponse(s.getId(), s.getPlanName(), s.getPriceCents(),
                s.getCurrency(), s.getBillingDay(), s.getStatus().name(),
                s.getCurrentPeriodStart(), s.getCurrentPeriodEnd(), s.getNextBillingAt());
    }

    private PaymentItem toPaymentItem(Payment p) {
        return new PaymentItem(p.getId(), p.getAmountCents(), p.getCurrency(),
                p.getStatus().name(), p.getProviderRef(),
                p.getDueAt(), p.getPaidAt(), p.getCreatedAt());
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }
}

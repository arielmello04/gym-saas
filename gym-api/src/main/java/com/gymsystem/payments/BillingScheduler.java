package com.gymsystem.payments;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled billing job — processes pending payments whose dueAt has passed.
 * Can also be triggered manually via {@link AdminBillingController}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingScheduler {

    private final PaymentRepository    paymentRepository;
    private final SubscriptionService  subscriptionService;

    /**
     * Runs every 30 minutes to pick up any due pending payments.
     */
    @Scheduled(fixedDelayString = "${billing.scheduler.interval-ms:1800000}")
    public void run() {
        List<Payment> due = paymentRepository.findDuePending(Instant.now());
        if (due.isEmpty()) return;

        log.info("[BillingScheduler] Processing {} due payment(s)", due.size());

        int ok = 0, fail = 0;
        for (Payment payment : due) {
            try {
                subscriptionService.processPayment(payment);
                ok++;
            } catch (Exception e) {
                fail++;
                log.error("[BillingScheduler] Failed to process payment id={}: {}",
                        payment.getId(), e.getMessage());
            }
        }
        log.info("[BillingScheduler] Done — ok={} failed={}", ok, fail);
    }
}

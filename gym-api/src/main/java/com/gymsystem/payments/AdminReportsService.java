package com.gymsystem.payments;

import com.gymsystem.tenant.context.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminReportsService {

    private final PaymentRepository      paymentRepository;
    private final SubscriptionRepository subscriptionRepository;

    // ── Revenue ───────────────────────────────────────────────

    /**
     * Returns total revenue (paid payments) grouped by month (yyyy-MM)
     * for the current tenant within [from, to].
     */
    public List<MonthlyRevenue> revenueByMonth(Instant from, Instant to) {
        Long tenantId = TenantGuard.currentTenantId();

        Map<String, Long> map = new LinkedHashMap<>();
        populateMonthKeys(from, to, map);

        paymentRepository.findByTenantIdAndStatusAndDueAtBetween(
                tenantId, PaymentStatus.PAID, from, to)
                .forEach(p -> {
                    String key = monthKey(p.getPaidAt() != null ? p.getPaidAt() : p.getDueAt());
                    map.merge(key, p.getAmountCents(), Long::sum);
                });

        return map.entrySet().stream()
                .map(e -> new MonthlyRevenue(e.getKey(), e.getValue()))
                .toList();
    }

    // ── Churn ─────────────────────────────────────────────────

    /**
     * Returns number of subscriptions canceled per month within [from, to].
     */
    public List<MonthlyChurn> churnByMonth(Instant from, Instant to) {
        Long tenantId = TenantGuard.currentTenantId();

        Map<String, Long> map = new LinkedHashMap<>();
        populateMonthKeys(from, to, map);

        subscriptionRepository
                .findByTenantIdAndStatusIn(tenantId, List.of(SubscriptionStatus.CANCELED))
                .stream()
                .filter(s -> s.getCanceledAt() != null
                        && !s.getCanceledAt().isBefore(from)
                        && s.getCanceledAt().isBefore(to))
                .forEach(s -> {
                    String key = monthKey(s.getCanceledAt());
                    map.merge(key, 1L, Long::sum);
                });

        return map.entrySet().stream()
                .map(e -> new MonthlyChurn(e.getKey(), e.getValue().intValue()))
                .toList();
    }

    // ── DTOs ──────────────────────────────────────────────────

    public record MonthlyRevenue(String month, long totalCents) {}
    public record MonthlyChurn(String month, int canceled) {}

    // ── Helpers ───────────────────────────────────────────────

    private String monthKey(Instant instant) {
        ZonedDateTime zdt = instant.atZone(ZoneOffset.UTC);
        return String.format("%04d-%02d", zdt.getYear(), zdt.getMonthValue());
    }

    private void populateMonthKeys(Instant from, Instant to, Map<String, Long> map) {
        YearMonth cur = YearMonth.from(from.atZone(ZoneOffset.UTC));
        YearMonth end = YearMonth.from(to.atZone(ZoneOffset.UTC));
        while (!cur.isAfter(end)) {
            map.put(String.format("%04d-%02d", cur.getYear(), cur.getMonthValue()), 0L);
            cur = cur.plusMonths(1);
        }
    }
}

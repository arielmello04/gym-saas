package com.gymsystem.payments.dto;

import com.gymsystem.payments.Subscription;

import java.time.Instant;

/** Linha da lista de assinaturas no painel administrativo. */
public record AdminSubscriptionItem(
        Long    id,
        String  userEmail,
        String  planName,
        long    priceCents,
        double  priceReais,
        String  currency,
        String  status,
        int     billingDay,
        Instant currentPeriodEnd,
        Instant nextBillingAt,
        Instant canceledAt
) {
    public static AdminSubscriptionItem from(Subscription s) {
        return new AdminSubscriptionItem(
                s.getId(),
                s.getUser().getEmail(),
                s.getPlanName(),
                s.getPriceCents(),
                s.getPriceCents() / 100.0,
                s.getCurrency(),
                s.getStatus().name(),
                s.getBillingDay(),
                s.getCurrentPeriodEnd(),
                s.getNextBillingAt(),
                s.getCanceledAt()
        );
    }
}

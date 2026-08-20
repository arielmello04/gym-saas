package com.gymsystem.payments.plan.dto;

import com.gymsystem.payments.plan.MembershipPlan;

/**
 * Plano como o catalogo expoe.
 *
 * priceReais vem calculado do servidor para a tela nao ter que dividir por 100
 * em cada lugar - e um dos jeitos classicos de aparecer diferenca de centavo.
 */
public record PlanResponse(
        Long    id,
        String  code,
        String  name,
        String  description,
        long    priceCents,
        double  priceReais,
        String  currency,
        int     intervalMonths,
        String  intervalLabel,
        boolean active
) {
    public static PlanResponse from(MembershipPlan p) {
        return new PlanResponse(
                p.getId(),
                p.getCode(),
                p.getName(),
                p.getDescription(),
                p.getPriceCents(),
                p.getPriceCents() / 100.0,
                p.getCurrency(),
                p.getIntervalMonths(),
                intervalLabel(p.getIntervalMonths()),
                p.isActive()
        );
    }

    private static String intervalLabel(int meses) {
        return switch (meses) {
            case 1  -> "mês";
            case 3  -> "trimestre";
            case 6  -> "semestre";
            case 12 -> "ano";
            default -> meses + " meses";
        };
    }
}

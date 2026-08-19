// src/main/java/com/gymsystem/checkin/dto/CheckinItem.java
package com.gymsystem.checkin.dto;

import com.gymsystem.checkin.Checkin;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * One row of the user's check-in history.
 *
 * Exists so the endpoint stops returning the Checkin entity: it carries lazy
 * associations to User and Tenant, and with open-in-view enabled Jackson
 * resolves them and serializes everything behind them.
 */
@Data
@AllArgsConstructor
public class CheckinItem {

    private Long    id;
    private String  provider;    // WELLHUB | TOTALPASS | DIRECT
    private String  gymName;
    private String  providerRef;
    private String  status;      // STARTED | COMPLETED | FAILED
    private String  partnerPlan;   // plano do aluno no parceiro
    private String  failureReason; // preenchido quando o parceiro recusa
    private Instant startedAt;
    private Instant completedAt;

    public static CheckinItem from(Checkin c) {
        return new CheckinItem(
                c.getId(),
                c.getProvider().name(),
                c.getGymName(),
                c.getProviderRef(),
                c.getStatus().name(),
                c.getPartnerPlan(),
                c.getFailureReason(),
                c.getStartedAt(),
                c.getCompletedAt()
        );
    }
}

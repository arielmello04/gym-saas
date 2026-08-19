package com.gymsystem.checkin;

import com.gymsystem.tenant.context.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Conciliacao de check-ins com os parceiros.
 *
 * Wellhub e TotalPass pagam por visita, e o repasse deles vem com a contagem do
 * lado do parceiro. Estes numeros sao o outro lado da conta - inclusive as
 * recusas, que costumam ser o que mais gera divergencia.
 */
@Service
@RequiredArgsConstructor
public class AdminCheckinService {

    private final CheckinRepository repository;

    public List<AdminCheckinItem> list(Instant from, Instant to, CheckinProvider provider) {
        Long tenantId = TenantGuard.currentTenantId();
        return repository
                .findByTenantIdAndStartedAtBetweenOrderByStartedAtDesc(tenantId, from, to)
                .stream()
                .filter(c -> provider == null || c.getProvider() == provider)
                .map(AdminCheckinItem::from)
                .toList();
    }

    /** Uma linha por parceiro: quantos entraram, quantos foram recusados. */
    public List<ProviderSummary> summary(Instant from, Instant to) {
        Long tenantId = TenantGuard.currentTenantId();

        Map<CheckinProvider, long[]> counters = new EnumMap<>(CheckinProvider.class);
        for (CheckinProvider p : CheckinProvider.values()) {
            counters.put(p, new long[3]); // [completed, failed, started]
        }

        repository.findByTenantIdAndStartedAtBetweenOrderByStartedAtDesc(tenantId, from, to)
                .forEach(c -> {
                    long[] row = counters.get(c.getProvider());
                    switch (c.getStatus()) {
                        case COMPLETED -> row[0]++;
                        case FAILED    -> row[1]++;
                        case STARTED   -> row[2]++;
                    }
                });

        return counters.entrySet().stream()
                .map(e -> new ProviderSummary(
                        e.getKey().name(),
                        e.getValue()[0],
                        e.getValue()[1],
                        e.getValue()[2],
                        e.getValue()[0] + e.getValue()[1] + e.getValue()[2]))
                .toList();
    }

    public record ProviderSummary(
            String provider,
            long completed,
            long failed,
            long pending,
            long total
    ) {}

    /** Linha da visao administrativa: inclui quem entrou, o que a do aluno nao precisa. */
    public record AdminCheckinItem(
            Long id,
            String provider,
            String memberEmail,
            String partnerMemberRef,
            String partnerPlan,
            String status,
            String failureReason,
            Instant startedAt,
            Instant completedAt
    ) {
        static AdminCheckinItem from(Checkin c) {
            return new AdminCheckinItem(
                    c.getId(),
                    c.getProvider().name(),
                    c.getUser().getEmail(),
                    c.getPartnerMemberRef(),
                    c.getPartnerPlan(),
                    c.getStatus().name(),
                    c.getFailureReason(),
                    c.getStartedAt(),
                    c.getCompletedAt()
            );
        }
    }
}

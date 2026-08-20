package com.gymsystem.payments;

import com.gymsystem.payments.dto.AdminSubscriptionItem;
import com.gymsystem.tenant.context.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminSubscriptionService {

    private final SubscriptionRepository repository;

    public List<AdminSubscriptionItem> list() {
        Long tenantId = TenantGuard.currentTenantId();
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(AdminSubscriptionItem::from)
                .toList();
    }
}

package com.gymsystem.checkin;

import com.gymsystem.checkin.dto.StartCheckinRequest;
import com.gymsystem.checkin.dto.StartCheckinResponse;
import com.gymsystem.tenant.TenantRepository;
import com.gymsystem.tenant.context.TenantGuard;
import com.gymsystem.user.User;
import com.gymsystem.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckinService {

    private final CheckinRepository repository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    @Value("${checkin.callback-secret}")
    private String callbackSecret;

    @Transactional
    public StartCheckinResponse start(StartCheckinRequest req) {
        Long tenantId = TenantGuard.currentTenantId();
        var  tenant   = tenantRepository.findById(tenantId).orElseThrow();
        User user     = currentUser();

        CheckinProvider provider  = CheckinProvider.valueOf(req.getProvider().toUpperCase());
        String          providerRef = "CHK-" + UUID.randomUUID();

        Checkin c = Checkin.builder()
                .user(user)
                .tenant(tenant)
                .provider(provider)
                .gymName(provider == CheckinProvider.DIRECT ? req.getGymName() : null)
                .providerRef(providerRef)
                .status(CheckinStatus.STARTED)
                .startedAt(Instant.now())
                .build();
        var saved = repository.save(c);

        if (provider == CheckinProvider.GYMPASS || provider == CheckinProvider.TOTALPASS) {
            String redirect = "gymsystem://checkin/provider?ref=" + providerRef + "&provider=" + provider.name();
            return new StartCheckinResponse(redirect, saved.getId());
        }

        c.setStatus(CheckinStatus.COMPLETED);
        c.setCompletedAt(Instant.now());
        repository.save(c);
        return new StartCheckinResponse(null, saved.getId());
    }

    @Transactional
    public void providerCallback(String providedSecret, String providerRef, boolean approved) {
        if (!callbackSecret.equals(providedSecret))
            throw new SecurityException("Invalid provider secret");

        Long tenantId = TenantGuard.currentTenantId();
        var c = repository.findByProviderRefAndTenantId(providerRef, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown providerRef"));

        if (approved) {
            c.setStatus(CheckinStatus.COMPLETED);
            c.setCompletedAt(Instant.now());
        } else {
            c.setStatus(CheckinStatus.FAILED);
        }
        repository.save(c);
    }

    public List<Checkin> myHistory() {
        Long tenantId = TenantGuard.currentTenantId();
        return repository.findByUserIdAndTenantIdOrderByStartedAtDesc(currentUser().getId(), tenantId);
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }
}

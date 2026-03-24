package com.gymsystem.booking;

import com.gymsystem.booking.dto.GenerateMonthRequest;
import com.gymsystem.tenant.TenantRepository;
import com.gymsystem.tenant.context.TenantGuard;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MonthlyScheduleGenerator {

    private final ClassTypeRepository    classTypeRepository;
    private final ClassSessionRepository classSessionRepository;
    private final TenantRepository       tenantRepository;

    @Transactional
    public int generate(GenerateMonthRequest req, Long adminId) {
        Long tenantId = TenantGuard.currentTenantId();
        var  tenant   = tenantRepository.findById(tenantId).orElseThrow();

        var classType = classTypeRepository
                .findByCodeAndActiveTrueAndTenantId(req.getClassTypeCode(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown or inactive class type: " + req.getClassTypeCode()));

        YearMonth ym    = YearMonth.of(req.getYear(), req.getMonth());
        LocalDate first = ym.atDay(1);
        LocalDate last  = ym.atEndOfMonth();
        LocalTime start = LocalTime.parse(req.getStartTime());
        LocalTime end   = LocalTime.parse(req.getEndTime());
        ZoneId    zone  = ZoneId.of(req.getTimezone() != null ? req.getTimezone() : "UTC");

        List<ClassSession> sessions = new ArrayList<>();
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            if (req.getDaysOfWeek() != null && !req.getDaysOfWeek().isEmpty()
                    && !req.getDaysOfWeek().contains(date.getDayOfWeek().getValue())) {
                continue;
            }

            Instant startAt = date.atTime(start).atZone(zone).toInstant();
            Instant endAt   = date.atTime(end).atZone(zone).toInstant();

            boolean exists = classSessionRepository
                    .findActiveSessionsBetween(tenantId, startAt, endAt)
                    .stream()
                    .anyMatch(s -> s.getClassType().getId().equals(classType.getId()));
            if (exists) continue;

            sessions.add(ClassSession.builder()
                    .classType(classType)
                    .tenant(tenant)
                    .startAt(startAt)
                    .endAt(endAt)
                    .capacity(req.getCapacity())
                    .canceled(false)
                    .createdByAdminId(adminId)
                    .createdAt(Instant.now())
                    .build());
        }

        classSessionRepository.saveAll(sessions);
        return sessions.size();
    }
}
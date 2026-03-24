package com.gymsystem.booking;

import com.gymsystem.booking.config.BookingConfigService;
import com.gymsystem.booking.dto.CalendarItem;
import com.gymsystem.tenant.context.TenantGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/classes/calendar")
@RequiredArgsConstructor
@Tag(name = "Calendar", description = "Browse the class schedule calendar with booking status")
public class CalendarController {

    private final ClassSessionRepository  sessionRepo;
    private final BookingRepository       bookingRepo;
    private final BookingPolicyRepository policyRepo;
    private final BookingConfigService    bookingConfigService;

    @Operation(summary = "List sessions in a date range with availability and booking state (supports type filter and onlyOpen flag)")
    @GetMapping
    public ResponseEntity<List<CalendarItem>> calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(name = "type",     required = false)       String  typeCode,
            @RequestParam(name = "onlyOpen", defaultValue = "false") boolean onlyOpen
    ) {
        if (from == null || to == null || !from.isBefore(to))
            throw new IllegalArgumentException("Invalid date range");

        Long tenantId = TenantGuard.currentTenantId();

        var sessions = sessionRepo.findCalendar(tenantId, from, to, typeCode);
        var now      = Instant.now();
        var cfg      = bookingConfigService.get();
        var policy   = policyRepo.findTopByTenantIdOrderByIdAsc(tenantId).orElse(null);

        List<CalendarItem> items = new ArrayList<>(sessions.size());
        for (var s : sessions) {
            long booked    = bookingRepo.countActiveBySessionId(s.getId(), tenantId);
            long spotsLeft = Math.max(0, s.getCapacity() - booked);

            var z        = s.getStartAt().atZone(ZoneOffset.UTC);
            var firstDay = z.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            var openAt   = firstDay.minusDays(cfg.getPublishDaysBeforeMonth());

            boolean withinHorizon = true;
            if (policy != null) {
                Instant horizon = now.plus(policy.getOpenDaysInAdvance(), ChronoUnit.DAYS);
                withinHorizon = !s.getStartAt().isAfter(horizon);
            }

            boolean openForBooking =
                    !s.isCanceled()
                            && now.isBefore(s.getStartAt())
                            && !ZonedDateTime.now(ZoneOffset.UTC).isBefore(openAt)
                            && withinHorizon;

            if (onlyOpen && (!openForBooking || spotsLeft <= 0)) continue;

            items.add(new CalendarItem(
                    s.getId(),
                    s.getClassType().getCode(),
                    s.getClassType().getName(),
                    s.getStartAt(),
                    s.getEndAt(),
                    s.getCapacity(),
                    booked,
                    spotsLeft,
                    s.isCanceled(),
                    openForBooking
            ));
        }
        return ResponseEntity.ok(items);
    }
}
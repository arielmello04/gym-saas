package com.gymsystem.booking.waitlist;

import com.gymsystem.booking.dto.BookingResponse;
import com.gymsystem.booking.waitlist.dto.WaitlistEntryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/waitlist")
@RequiredArgsConstructor
@Tag(name = "Waitlist", description = "Fila de espera para sessões lotadas")
public class WaitlistController {

    private final WaitlistService waitlistService;

    @Operation(summary = "Entrar na fila de espera de uma sessão lotada")
    @PostMapping("/{sessionId}/join")
    @ResponseStatus(HttpStatus.CREATED)
    public WaitlistEntryResponse join(@PathVariable Long sessionId) {
        return waitlistService.join(sessionId);
    }

    @Operation(summary = "Confirmar vaga promovida (deve ser feito antes de expiresAt)")
    @PostMapping("/{sessionId}/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse confirm(@PathVariable Long sessionId) {
        var booking = waitlistService.confirm(sessionId);
        return new BookingResponse(
                booking.getId(),
                booking.getSession().getId(),
                booking.getStatus().name(),
                booking.getCreatedAt(),
                booking.getCanceledAt()
        );
    }

    @Operation(summary = "Sair da fila de espera voluntariamente")
    @DeleteMapping("/{sessionId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable Long sessionId) {
        waitlistService.leave(sessionId);
    }

    @Operation(summary = "Listar minhas entradas ativas na fila de espera")
    @GetMapping("/me")
    public List<WaitlistEntryResponse> myEntries() {
        return waitlistService.myEntries();
    }
}

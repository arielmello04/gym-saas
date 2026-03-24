package com.gymsystem.notifications;

import com.gymsystem.booking.ClassSession;
import com.gymsystem.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Development email service — prints all emails to the log.
 * Active when notifications.email.provider=console (default).
 */
@Slf4j
@Service
@ConditionalOnProperty(
    name = "notifications.email.provider",
    havingValue = "console",
    matchIfMissing = true
)
public class ConsoleEmailService implements EmailService {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneOffset.UTC);

    @Override
    public void sendPaymentReminder(User user, String subject, String body) {
        log.info("[EMAIL] To: {} | Subject: {} | Body: {}", user.getEmail(), subject, body);
    }

    @Override
    public void sendPaymentConfirmed(User user, long amountCents, String currency, String planName) {
        log.info("[EMAIL] To: {} | Pagamento confirmado — {} {} | Plano: {}",
                user.getEmail(), currency, amountCents / 100.0, planName);
    }

    @Override
    public void sendPaymentFailed(User user, String planName) {
        log.warn("[EMAIL] To: {} | Pagamento falhou — Plano: {}", user.getEmail(), planName);
    }

    @Override
    public void sendWaitlistPromotion(User user, ClassSession session, Instant expiresAt) {
        log.info("[EMAIL] To: {} | Vaga disponível na fila de espera! Sessão {} em {} — confirme até {}",
                user.getEmail(),
                session.getClassType().getName(),
                FMT.format(session.getStartAt()),
                FMT.format(expiresAt));
    }

    @Override
    public void sendWaitlistExpired(User user, ClassSession session) {
        log.info("[EMAIL] To: {} | Prazo expirado — vaga na sessão {} em {} foi para o próximo da fila",
                user.getEmail(),
                session.getClassType().getName(),
                FMT.format(session.getStartAt()));
    }

    @Override
    public void sendBookingConfirmed(User user, ClassSession session) {
        log.info("[EMAIL] To: {} | Reserva confirmada — {} em {}",
                user.getEmail(),
                session.getClassType().getName(),
                FMT.format(session.getStartAt()));
    }

    @Override
    public void sendBookingCanceled(User user, ClassSession session) {
        log.info("[EMAIL] To: {} | Reserva cancelada — {} em {}",
                user.getEmail(),
                session.getClassType().getName(),
                FMT.format(session.getStartAt()));
    }

    @Override
    public void sendInvite(String toEmail, String inviteCode, String tenantName, Instant expiresAt) {
        log.info("[EMAIL] To: {} | Convite para {} — código: {} | expira: {}",
                toEmail, tenantName, inviteCode, FMT.format(expiresAt));
    }
}

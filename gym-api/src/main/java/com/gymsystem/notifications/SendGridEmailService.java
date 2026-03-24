package com.gymsystem.notifications;

import com.gymsystem.booking.ClassSession;
import com.gymsystem.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * SendGrid email service via REST API (no SDK dependency needed).
 *
 * Activate with: notifications.email.provider=sendgrid
 *
 * Required config:
 *   notifications.sendgrid.api-key=SG.xxxxx
 *   notifications.email.from=noreply@seugym.com.br
 *   notifications.email.from-name=GymSystem
 *
 * Docs: https://docs.sendgrid.com/api-reference/mail-send/mail-send
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "notifications.email.provider", havingValue = "sendgrid")
@RequiredArgsConstructor
public class SendGridEmailService implements EmailService {

    private static final String SENDGRID_URL = "https://api.sendgrid.com/v3/mail/send";
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(ZoneOffset.UTC);

    @Value("${notifications.sendgrid.api-key}")
    private String apiKey;

    @Value("${notifications.email.from}")
    private String fromAddress;

    @Value("${notifications.email.from-name:GymSystem}")
    private String fromName;

    private final RestTemplate restTemplate = new RestTemplate();

    // ── Interface implementation ──────────────────────────────

    @Override @Async
    public void sendPaymentReminder(User user, String subject, String body) {
        send(user.getEmail(), subject, "<p>" + body + "</p>");
    }

    @Override @Async
    public void sendPaymentConfirmed(User user, long amountCents, String currency, String planName) {
        send(user.getEmail(),
                "Pagamento confirmado — " + planName,
                buildHtml("Pagamento Confirmado ✅",
                        "Olá <strong>%s</strong>, seu pagamento de <strong>%s %.2f</strong> foi processado! Sua assinatura está ativa. 💪"
                        .formatted(user.getEmail(), currency, amountCents / 100.0)));
    }

    @Override @Async
    public void sendPaymentFailed(User user, String planName) {
        send(user.getEmail(),
                "Ação necessária — pagamento falhou",
                buildHtml("Pagamento Falhou ⚠️",
                        "Olá <strong>%s</strong>, não conseguimos processar seu pagamento do plano <strong>%s</strong>. Suas reservas foram suspensas. Por favor, atualize seu método de pagamento."
                        .formatted(user.getEmail(), planName)));
    }

    @Override @Async
    public void sendWaitlistPromotion(User user, ClassSession session, Instant expiresAt) {
        send(user.getEmail(),
                "Vaga disponível — confirme agora!",
                buildHtml("Sua Vez na Fila de Espera! 🎉",
                        "Uma vaga abriu para <strong>%s</strong> em <strong>%s</strong>. Confirme até <strong>%s</strong> no app."
                        .formatted(session.getClassType().getName(),
                                FMT.format(session.getStartAt()),
                                FMT.format(expiresAt))));
    }

    @Override @Async
    public void sendWaitlistExpired(User user, ClassSession session) {
        send(user.getEmail(),
                "Prazo expirado — vaga cedida",
                buildHtml("Prazo Expirado ⏰",
                        "Seu prazo para confirmar a vaga em <strong>%s</strong> (%s) expirou. A vaga foi cedida ao próximo da fila."
                        .formatted(session.getClassType().getName(),
                                FMT.format(session.getStartAt()))));
    }

    @Override @Async
    public void sendBookingConfirmed(User user, ClassSession session) {
        send(user.getEmail(),
                "Reserva confirmada — " + session.getClassType().getName(),
                buildHtml("Reserva Confirmada ✅",
                        "Sua reserva para <strong>%s</strong> em <strong>%s</strong> está confirmada! 🏋️"
                        .formatted(session.getClassType().getName(),
                                FMT.format(session.getStartAt()))));
    }

    @Override @Async
    public void sendBookingCanceled(User user, ClassSession session) {
        send(user.getEmail(),
                "Reserva cancelada — " + session.getClassType().getName(),
                buildHtml("Reserva Cancelada",
                        "Sua reserva para <strong>%s</strong> em <strong>%s</strong> foi cancelada."
                        .formatted(session.getClassType().getName(),
                                FMT.format(session.getStartAt()))));
    }

    @Override @Async
    public void sendInvite(String toEmail, String inviteCode, String tenantName, Instant expiresAt) {
        send(toEmail,
                "Convite para " + tenantName,
                buildHtml("Convite para " + tenantName + " 🏃",
                        "Use o código <strong style='font-size:22px;letter-spacing:4px;'>%s</strong> no app para se cadastrar em <strong>%s</strong>. Expira em %s."
                        .formatted(inviteCode, tenantName, FMT.format(expiresAt))));
    }

    // ── Helpers ───────────────────────────────────────────────

    private void send(String toEmail, String subject, String htmlContent) {
        try {
            var payload = Map.of(
                "personalizations", List.of(Map.of(
                    "to", List.of(Map.of("email", toEmail))
                )),
                "from", Map.of("email", fromAddress, "name", fromName),
                "subject", subject,
                "content", List.of(Map.of(
                    "type",  "text/html",
                    "value", htmlContent
                ))
            );

            var headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> resp = restTemplate.exchange(
                    SENDGRID_URL, HttpMethod.POST,
                    new HttpEntity<>(payload, headers), String.class);

            if (resp.getStatusCode().is2xxSuccessful()) {
                log.debug("[SendGrid] Email sent to {} — {}", toEmail, subject);
            } else {
                log.error("[SendGrid] HTTP {} for email to {}", resp.getStatusCode(), toEmail);
            }
        } catch (Exception e) {
            log.error("[SendGrid] Failed to send email to {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildHtml(String title, String body) {
        return """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;">
              <div style="background:#1a1a2e;padding:20px;border-radius:8px 8px 0 0;">
                <h1 style="color:#fff;margin:0;font-size:20px;">💪 GymSystem</h1>
              </div>
              <div style="background:#fff;padding:24px;border-radius:0 0 8px 8px;
                          box-shadow:0 2px 8px rgba(0,0,0,.08);">
                <h2 style="color:#1a1a2e;">%s</h2>
                <p style="color:#444;line-height:1.6;">%s</p>
              </div>
              <p style="text-align:center;color:#999;font-size:12px;margin-top:16px;">
                GymSystem — E-mail automático, não responda.
              </p>
            </div>
            """.formatted(title, body);
    }
}

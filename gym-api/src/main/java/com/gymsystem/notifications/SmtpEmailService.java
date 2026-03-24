package com.gymsystem.notifications;

import com.gymsystem.booking.ClassSession;
import com.gymsystem.user.User;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Production SMTP email service using Spring Mail.
 *
 * Activate with: notifications.email.provider=smtp
 *
 * Required config:
 *   spring.mail.host=smtp.gmail.com       (ou outro SMTP)
 *   spring.mail.port=587
 *   spring.mail.username=seuemail@gmail.com
 *   spring.mail.password=sua-app-password
 *   spring.mail.properties.mail.smtp.auth=true
 *   spring.mail.properties.mail.smtp.starttls.enable=true
 *   notifications.email.from=noreply@seugym.com.br
 *   notifications.email.from-name=GymSystem
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "notifications.email.provider", havingValue = "smtp")
@RequiredArgsConstructor
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${notifications.email.from}")
    private String fromAddress;

    @Value("${notifications.email.from-name:GymSystem}")
    private String fromName;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(ZoneOffset.UTC);

    // ── Interface implementation ──────────────────────────────

    @Override @Async
    public void sendPaymentReminder(User user, String subject, String body) {
        send(user.getEmail(), subject, plainToHtml(body));
    }

    @Override @Async
    public void sendPaymentConfirmed(User user, long amountCents, String currency, String planName) {
        String html = template("Pagamento Confirmado ✅",
                "Seu pagamento foi processado com sucesso!",
                """
                <p>Olá <strong>%s</strong>,</p>
                <p>Recebemos seu pagamento de <strong>%s %.2f</strong> referente ao plano <strong>%s</strong>.</p>
                <p>Sua assinatura está ativa. Bons treinos! 💪</p>
                """.formatted(user.getEmail(), currency, amountCents / 100.0, planName)
        );
        send(user.getEmail(), "Pagamento confirmado — " + planName, html);
    }

    @Override @Async
    public void sendPaymentFailed(User user, String planName) {
        String html = template("Pagamento Falhou ⚠️",
                "Precisamos da sua atenção",
                """
                <p>Olá <strong>%s</strong>,</p>
                <p>Não conseguimos processar seu pagamento referente ao plano <strong>%s</strong>.</p>
                <p>Suas reservas futuras foram <strong>suspensas</strong> até a regularização.</p>
                <p>Por favor, acesse o app e atualize seu método de pagamento.</p>
                """.formatted(user.getEmail(), planName)
        );
        send(user.getEmail(), "Ação necessária — pagamento falhou", html);
    }

    @Override @Async
    public void sendWaitlistPromotion(User user, ClassSession session, Instant expiresAt) {
        String html = template("Sua Vez na Fila de Espera! 🎉",
                "Uma vaga se abriu para você",
                """
                <p>Olá <strong>%s</strong>,</p>
                <p>Uma vaga foi aberta para a aula de <strong>%s</strong> em <strong>%s</strong>.</p>
                <p>Você tem até <strong>%s</strong> para confirmar sua reserva no app.</p>
                <p><em>Se não confirmar a tempo, a vaga passará para o próximo da fila.</em></p>
                """.formatted(
                        user.getEmail(),
                        session.getClassType().getName(),
                        FMT.format(session.getStartAt()),
                        FMT.format(expiresAt)
                )
        );
        send(user.getEmail(), "Vaga disponível — confirme agora!", html);
    }

    @Override @Async
    public void sendWaitlistExpired(User user, ClassSession session) {
        String html = template("Prazo Expirado ⏰",
                "Sua vaga foi cedida ao próximo da fila",
                """
                <p>Olá <strong>%s</strong>,</p>
                <p>Infelizmente o prazo para confirmar sua vaga na aula de <strong>%s</strong>
                em <strong>%s</strong> expirou.</p>
                <p>A vaga foi oferecida ao próximo participante da fila de espera.</p>
                <p>Fique atento para a próxima oportunidade!</p>
                """.formatted(
                        user.getEmail(),
                        session.getClassType().getName(),
                        FMT.format(session.getStartAt())
                )
        );
        send(user.getEmail(), "Prazo expirado — vaga cedida", html);
    }

    @Override @Async
    public void sendBookingConfirmed(User user, ClassSession session) {
        String html = template("Reserva Confirmada ✅",
                "Sua reserva está garantida",
                """
                <p>Olá <strong>%s</strong>,</p>
                <p>Sua reserva para a aula de <strong>%s</strong> em <strong>%s</strong>
                foi confirmada com sucesso!</p>
                <p>Até lá! 🏋️</p>
                """.formatted(
                        user.getEmail(),
                        session.getClassType().getName(),
                        FMT.format(session.getStartAt())
                )
        );
        send(user.getEmail(), "Reserva confirmada — " + session.getClassType().getName(), html);
    }

    @Override @Async
    public void sendBookingCanceled(User user, ClassSession session) {
        String html = template("Reserva Cancelada",
                "Sua reserva foi cancelada",
                """
                <p>Olá <strong>%s</strong>,</p>
                <p>Sua reserva para a aula de <strong>%s</strong> em <strong>%s</strong>
                foi cancelada.</p>
                <p>Se precisar, você pode fazer uma nova reserva pelo app.</p>
                """.formatted(
                        user.getEmail(),
                        session.getClassType().getName(),
                        FMT.format(session.getStartAt())
                )
        );
        send(user.getEmail(), "Reserva cancelada — " + session.getClassType().getName(), html);
    }

    @Override @Async
    public void sendInvite(String toEmail, String inviteCode, String tenantName, Instant expiresAt) {
        String html = template("Convite para " + tenantName + " 🏃",
                "Você foi convidado!",
                """
                <p>Você recebeu um convite para se juntar à academia <strong>%s</strong>.</p>
                <p>Use o código abaixo no aplicativo para completar seu cadastro:</p>
                <div style="text-align:center;margin:24px 0;">
                  <span style="font-size:28px;font-weight:bold;letter-spacing:6px;
                               padding:12px 24px;background:#f0f0f0;border-radius:8px;">%s</span>
                </div>
                <p><em>O convite expira em %s.</em></p>
                """.formatted(tenantName, inviteCode, FMT.format(expiresAt))
        );
        send(toEmail, "Convite para " + tenantName, html);
    }

    // ── HTML helpers ──────────────────────────────────────────

    private String template(String title, String preheader, String content) {
        return """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head><meta charset="UTF-8">
              <title>%s</title>
            </head>
            <body style="margin:0;padding:0;background:#f4f4f7;font-family:Arial,sans-serif;">
              <span style="display:none;max-height:0;overflow:hidden;">%s</span>
              <table width="100%%" cellpadding="0" cellspacing="0">
                <tr><td align="center" style="padding:32px 16px;">
                  <table width="600" cellpadding="0" cellspacing="0"
                         style="background:#ffffff;border-radius:8px;overflow:hidden;
                                box-shadow:0 2px 8px rgba(0,0,0,.08);">
                    <!-- Header -->
                    <tr><td style="background:#1a1a2e;padding:24px 32px;">
                      <h1 style="margin:0;color:#ffffff;font-size:22px;">💪 GymSystem</h1>
                    </td></tr>
                    <!-- Title -->
                    <tr><td style="padding:32px 32px 0;">
                      <h2 style="margin:0;color:#1a1a2e;font-size:20px;">%s</h2>
                    </td></tr>
                    <!-- Body -->
                    <tr><td style="padding:16px 32px 32px;color:#444;font-size:15px;line-height:1.6;">
                      %s
                    </td></tr>
                    <!-- Footer -->
                    <tr><td style="background:#f4f4f7;padding:16px 32px;
                                   color:#999;font-size:12px;text-align:center;">
                      GymSystem — Este é um e-mail automático, não responda.
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(title, preheader, title, content);
    }

    private String plainToHtml(String text) {
        return "<p>" + text.replace("\n", "</p><p>") + "</p>";
    }

    private void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(msg);
            log.debug("[SMTP] Email sent to {} — {}", to, subject);
        } catch (Exception e) {
            log.error("[SMTP] Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}

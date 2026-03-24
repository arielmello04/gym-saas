package com.gymsystem.notifications;

import com.gymsystem.booking.ClassSession;
import com.gymsystem.user.User;
import java.time.Instant;

/**
 * Email notification contract.
 *
 * Implementations:
 *   - ConsoleEmailService   (dev — logs to stdout, always active in default profile)
 *   - SmtpEmailService      (production — activated via spring.mail.* config)
 *   - SendGridEmailService  (production — activated via notifications.email.provider=sendgrid)
 */
public interface EmailService {

    /** Generic payment status email (kept for backward compat). */
    void sendPaymentReminder(User user, String subject, String body);

    /** Sent when payment is successfully processed. */
    void sendPaymentConfirmed(User user, long amountCents, String currency, String planName);

    /** Sent when payment fails and subscription goes PAST_DUE. */
    void sendPaymentFailed(User user, String planName);

    /** Sent when a user is promoted from the waitlist and a spot opens. */
    void sendWaitlistPromotion(User user, ClassSession session, Instant expiresAt);

    /** Sent when a promoted user's confirmation window expires. */
    void sendWaitlistExpired(User user, ClassSession session);

    /** Sent when a user's booking is confirmed. */
    void sendBookingConfirmed(User user, ClassSession session);

    /** Sent when a user's booking is canceled. */
    void sendBookingCanceled(User user, ClassSession session);

    /** Sent when an admin creates an invite token for the user. */
    void sendInvite(String toEmail, String inviteCode, String tenantName, Instant expiresAt);
}

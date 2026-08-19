// src/main/java/com/gymsystem/payments/webhook/HmacVerifier.java
package com.gymsystem.payments.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * HMAC-SHA256 verification shared by the payment gateways.
 *
 * Every provider signs a different payload — Mercado Pago signs a manifest
 * string, Pagar.me signs the raw body, Stripe signs "timestamp.body" — but all
 * three send the result as a hex digest, so the comparison lives here.
 *
 * The comparison is constant-time on purpose: comparing signatures with
 * String.equals leaks, through response timing, how many leading characters a
 * forged signature got right, which is enough to reconstruct it byte by byte.
 */
public final class HmacVerifier {

    private HmacVerifier() {}

    /** Hex HMAC-SHA256 of {@code payload} under {@code secret}. */
    public static String hex(String secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    /**
     * True when {@code expectedHex} is the hex HMAC-SHA256 of {@code payload}.
     * Case-insensitive (providers differ) and constant-time.
     */
    public static boolean matchesHex(String secret, byte[] payload, String expectedHex) {
        if (secret == null || secret.isBlank() || expectedHex == null) return false;
        try {
            return constantTimeEquals(hex(secret, payload), expectedHex);
        } catch (Exception e) {
            return false;
        }
    }

    /** Same as {@link #matchesHex(String, byte[], String)} for a string payload. */
    public static boolean matchesHex(String secret, String payload, String expectedHex) {
        return matchesHex(secret, payload.getBytes(StandardCharsets.UTF_8), expectedHex);
    }

    /**
     * Compares two hex digests without short-circuiting on the first difference.
     *
     * Length is not secret here (a hex SHA-256 digest is always 64 chars), so
     * returning early on a length mismatch is fine.
     */
    private static boolean constantTimeEquals(String a, String b) {
        byte[] left  = a.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        byte[] right = b.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }
}

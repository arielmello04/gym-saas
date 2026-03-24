// src/main/java/com/gymsystem/payments/webhook/HmacVerifier.java
package com.gymsystem.payments.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/** Minimal HMAC-SHA256 verifier for provider webhooks. */
public class HmacVerifier {
    public static boolean verify(String secret, String payload, String signatureBase64) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes());
            String expected = Base64.getEncoder().encodeToString(digest);
            // Constant-time compare is recommended; simplified here
            return expected.equals(signatureBase64);
        } catch (Exception e) {
            return false;
        }
    }
}

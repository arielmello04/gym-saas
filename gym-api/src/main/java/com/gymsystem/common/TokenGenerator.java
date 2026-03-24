package com.gymsystem.common;

import java.security.SecureRandom;
import java.util.Base64;

/** Utility for generating secure, URL-safe tokens. */
public final class TokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenGenerator() {}

    /**
     * Generates a secure random token encoded with URL-safe Base64 (no padding).
     * @param byteLength number of random bytes (e.g., 32 or 48 for stronger tokens)
     */
    public static String generateUrlSafeToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

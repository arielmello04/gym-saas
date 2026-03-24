// src/main/java/com/gymsystem/common/ratelimit/RateLimiter.java
package com.gymsystem.common.ratelimit;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal in-memory rate limiter: enforces a minimum interval (ms) per key.
 * Good enough to protect from double-clicks behind a load balancer sticky session.
 */
@Component
public class RateLimiter {

    private final Map<String, Long> lastTouch = new ConcurrentHashMap<>();

    /**
     * Throws IllegalStateException if called again before "minIntervalMs" has elapsed for the given key.
     */
    public void enforceMinInterval(String key, long minIntervalMs, String errorMessage) {
        long now = System.currentTimeMillis();
        Long prev = lastTouch.put(key, now);
        if (prev != null && (now - prev) < minIntervalMs) {
            throw new IllegalStateException(errorMessage);
        }
    }
}

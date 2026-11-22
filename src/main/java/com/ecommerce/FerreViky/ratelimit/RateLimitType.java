package com.ecommerce.FerreViky.ratelimit;

import java.time.Duration;

/**
 * Request rate limit types. Each type has its own bucket per client,
 * so consuming one (e.g. login) does not affect the others (e.g. catalog).
 * The capacity is the number of requests allowed per period.
 */
public enum RateLimitType {

    AUTH(10, Duration.ofMinutes(1)),
    UPLOAD(10, Duration.ofMinutes(1)),
    PUBLIC(25, Duration.ofMinutes(1)),
    USER(20, Duration.ofMinutes(1)),
    ADMIN(40, Duration.ofMinutes(1)),

    /** /auth/resend-verification, limited per client IP. */
    RESEND_IP(3, Duration.ofMinutes(1)),
    /** /auth/resend-verification, limited per (hashed) email, applied in the controller. */
    RESEND_EMAIL(3, Duration.ofHours(1));

    private final int capacity;
    private final Duration period;

    RateLimitType(int capacity, Duration period) {
        this.capacity = capacity;
        this.period = period;
    }

    public int getCapacity() {
        return capacity;
    }

    public Duration getPeriod() {
        return period;
    }
}
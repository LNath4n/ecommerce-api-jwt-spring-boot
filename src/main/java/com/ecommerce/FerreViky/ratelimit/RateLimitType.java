package com.ecommerce.FerreViky.ratelimit;
/**
 * Request rate limit types. Each type has its own bucket per client,
 * so consuming one (e.g. login) does not affect the others (e.g. catalog).
 * The capacity is the number of requests allowed per minute.
 */
public enum RateLimitType {

    AUTH(10),
    UPLOAD(10),
    PUBLIC(45),
    USER(30),
    ADMIN(60);

    private final int capacity;

    RateLimitType(int capacity) {
        this.capacity = capacity;
    }

    public int getCapacity() {
        return capacity;
    }
}
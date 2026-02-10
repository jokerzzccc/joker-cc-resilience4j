package com.joker.resilience4j.model;

public record CombinedRateLimiterStatus(
        RateLimiterStatus primary,
        RateLimiterStatus secondary
) {
}

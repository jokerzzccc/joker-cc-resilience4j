package com.joker.resilience4j.model;

public record RateLimiterStatus(
        String name,
        int availablePermissions,
        int numberOfWaitingThreads
) {
}

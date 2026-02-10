package com.joker.resilience4j.model;

public record MetricsSnapshot(
        double circuitBreakerTotalCalls,
        double rateLimiterTotalCalls,
        long apiCallCount,
        double apiCallTotalTimeMs,
        double apiCallMeanTimeMs,
        double apiCallMaxTimeMs
) {
}

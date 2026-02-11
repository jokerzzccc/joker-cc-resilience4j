package com.joker.resilience4j.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.resilience")
public record ResilienceConfigProperties(
        @DefaultValue CircuitBreakerProps circuitBreaker,
        @DefaultValue RateLimiterProps rateLimiter
) {

    public record CircuitBreakerProps(
            @DefaultValue("50") float failureRateThreshold,
            @DefaultValue("50") float slowCallRateThreshold,
            @DefaultValue("2000") int slowCallDurationThresholdMs,
            @DefaultValue("10") int slidingWindowSize,
            @DefaultValue("5") int minimumNumberOfCalls,
            @DefaultValue("2") int permittedNumberOfCallsInHalfOpenState,
            @DefaultValue("5000") int waitDurationInOpenStateMs
    ) {
    }

    public record RateLimiterProps(
            @DefaultValue("10") int limitForPeriod,
            @DefaultValue("1000") int limitRefreshPeriodMs,
            @DefaultValue("0") int timeoutDurationMs
    ) {
    }
}

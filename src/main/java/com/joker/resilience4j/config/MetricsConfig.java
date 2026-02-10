package com.joker.resilience4j.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRateLimiterMetrics;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public MeterBinder circuitBreakerMetrics(CircuitBreakerRegistry circuitBreakerRegistry) {
        return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry);
    }

    @Bean
    public MeterBinder rateLimiterMetrics(RateLimiterRegistry rateLimiterRegistry) {
        return TaggedRateLimiterMetrics.ofRateLimiterRegistry(rateLimiterRegistry);
    }

    @Bean
    public Counter circuitBreakerCallCounter(MeterRegistry meterRegistry) {
        return Counter.builder("resilience4j.custom.circuitbreaker.calls.total")
                .description("Total CircuitBreaker calls through the service layer")
                .tag("component", "circuitbreaker")
                .register(meterRegistry);
    }

    @Bean
    public Counter rateLimiterCallCounter(MeterRegistry meterRegistry) {
        return Counter.builder("resilience4j.custom.ratelimiter.calls.total")
                .description("Total RateLimiter calls through the service layer")
                .tag("component", "ratelimiter")
                .register(meterRegistry);
    }

    @Bean
    public Timer apiCallTimer(MeterRegistry meterRegistry) {
        return Timer.builder("resilience4j.custom.api.call.duration")
                .description("API call duration through resilience4j components")
                .tag("component", "api")
                .register(meterRegistry);
    }

    @Bean
    public ResilienceMetricsRegistrar resilienceMetricsRegistrar(
            MeterRegistry meterRegistry,
            CircuitBreaker circuitBreaker,
            RateLimiter rateLimiter
    ) {
        return new ResilienceMetricsRegistrar(meterRegistry, circuitBreaker, rateLimiter);
    }
}

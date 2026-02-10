package com.joker.resilience4j.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class MetricsConfigTest {

    @Test
    void shouldCreateCircuitBreakerMetricsBinder() {
        MetricsConfig metricsConfig = new MetricsConfig();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();

        MeterBinder binder = metricsConfig.circuitBreakerMetrics(registry);

        assertThat(binder).isNotNull();
    }

    @Test
    void shouldCreateRateLimiterMetricsBinder() {
        MetricsConfig metricsConfig = new MetricsConfig();
        RateLimiterRegistry registry = RateLimiterRegistry.ofDefaults();

        MeterBinder binder = metricsConfig.rateLimiterMetrics(registry);

        assertThat(binder).isNotNull();
    }

    @Test
    void shouldCreateCircuitBreakerCallCounter() {
        MetricsConfig metricsConfig = new MetricsConfig();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        Counter counter = metricsConfig.circuitBreakerCallCounter(meterRegistry);

        assertThat(counter).isNotNull();
        assertThat(counter.getId().getName())
                .isEqualTo("resilience4j.custom.circuitbreaker.calls.total");
        assertThat(counter.getId().getTag("component")).isEqualTo("circuitbreaker");
    }

    @Test
    void shouldCreateRateLimiterCallCounter() {
        MetricsConfig metricsConfig = new MetricsConfig();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        Counter counter = metricsConfig.rateLimiterCallCounter(meterRegistry);

        assertThat(counter).isNotNull();
        assertThat(counter.getId().getName())
                .isEqualTo("resilience4j.custom.ratelimiter.calls.total");
        assertThat(counter.getId().getTag("component")).isEqualTo("ratelimiter");
    }

    @Test
    void shouldCreateApiCallTimer() {
        MetricsConfig metricsConfig = new MetricsConfig();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        Timer timer = metricsConfig.apiCallTimer(meterRegistry);

        assertThat(timer).isNotNull();
        assertThat(timer.getId().getName())
                .isEqualTo("resilience4j.custom.api.call.duration");
        assertThat(timer.getId().getTag("component")).isEqualTo("api");
    }

    @Test
    void shouldCreateResilienceMetricsRegistrar() {
        MetricsConfig metricsConfig = new MetricsConfig();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CircuitBreaker cb = CircuitBreaker.of("testCb", CircuitBreakerConfig.ofDefaults());
        RateLimiter rl = RateLimiter.of("testRl", RateLimiterConfig.ofDefaults());

        ResilienceMetricsRegistrar registrar =
                metricsConfig.resilienceMetricsRegistrar(meterRegistry, cb, rl);

        assertThat(registrar).isNotNull();
        assertThat(registrar.getMeterRegistry()).isSameAs(meterRegistry);
    }
}

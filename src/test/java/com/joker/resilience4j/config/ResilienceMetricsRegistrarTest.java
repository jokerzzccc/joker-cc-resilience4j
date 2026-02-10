package com.joker.resilience4j.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ResilienceMetricsRegistrarTest {

    @Test
    void shouldRegisterCircuitBreakerGauges() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CircuitBreaker cb = CircuitBreaker.of("testCb", CircuitBreakerConfig.ofDefaults());
        RateLimiter rl = RateLimiter.of("testRl", RateLimiterConfig.ofDefaults());

        new ResilienceMetricsRegistrar(registry, cb, rl);

        assertThat(findGauge(registry, "resilience4j.circuitbreaker.state", "testCb")).isNotNull();
        assertThat(findGauge(registry, "resilience4j.circuitbreaker.failure.rate", "testCb")).isNotNull();
        assertThat(findGauge(registry, "resilience4j.circuitbreaker.slow.call.rate", "testCb")).isNotNull();
        assertThat(findGauge(registry, "resilience4j.circuitbreaker.buffered.calls", "testCb")).isNotNull();
        assertThat(findGauge(registry, "resilience4j.circuitbreaker.failed.calls", "testCb")).isNotNull();
        assertThat(findGauge(registry, "resilience4j.circuitbreaker.not.permitted.calls", "testCb")).isNotNull();
    }

    @Test
    void shouldRegisterRateLimiterGauges() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CircuitBreaker cb = CircuitBreaker.of("testCb", CircuitBreakerConfig.ofDefaults());
        RateLimiter rl = RateLimiter.of("testRl", RateLimiterConfig.ofDefaults());

        new ResilienceMetricsRegistrar(registry, cb, rl);

        assertThat(findGauge(registry, "resilience4j.ratelimiter.available.permissions", "testRl")).isNotNull();
        assertThat(findGauge(registry, "resilience4j.ratelimiter.waiting.threads", "testRl")).isNotNull();
    }

    @Test
    void shouldReflectCircuitBreakerMetricsValues() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(5)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(50)
                .build();
        CircuitBreaker cb = CircuitBreaker.of("metricsCb", config);
        RateLimiter rl = RateLimiter.of("metricsRl", RateLimiterConfig.ofDefaults());

        new ResilienceMetricsRegistrar(registry, cb, rl);

        Gauge stateGauge = findGauge(registry, "resilience4j.circuitbreaker.state", "metricsCb");
        assertThat(stateGauge).isNotNull();
        assertThat(stateGauge.value()).isEqualTo(CircuitBreaker.State.CLOSED.getOrder());

        cb.onSuccess(0, TimeUnit.MILLISECONDS);
        cb.onError(0, TimeUnit.MILLISECONDS, new RuntimeException("test"));
        cb.onSuccess(0, TimeUnit.MILLISECONDS);

        Gauge bufferedGauge = findGauge(registry, "resilience4j.circuitbreaker.buffered.calls", "metricsCb");
        assertThat(bufferedGauge).isNotNull();
        assertThat(bufferedGauge.value()).isEqualTo(3.0);

        Gauge failureRateGauge = findGauge(registry, "resilience4j.circuitbreaker.failure.rate", "metricsCb");
        assertThat(failureRateGauge).isNotNull();
        assertThat(failureRateGauge.value()).isGreaterThanOrEqualTo(0.0);

        Gauge slowCallRateGauge = findGauge(registry, "resilience4j.circuitbreaker.slow.call.rate", "metricsCb");
        assertThat(slowCallRateGauge).isNotNull();
        assertThat(slowCallRateGauge.value()).isGreaterThanOrEqualTo(0.0);

        Gauge failedCallsGauge = findGauge(registry, "resilience4j.circuitbreaker.failed.calls", "metricsCb");
        assertThat(failedCallsGauge).isNotNull();
        assertThat(failedCallsGauge.value()).isEqualTo(1.0);

        Gauge notPermittedGauge = findGauge(registry, "resilience4j.circuitbreaker.not.permitted.calls", "metricsCb");
        assertThat(notPermittedGauge).isNotNull();
        assertThat(notPermittedGauge.value()).isEqualTo(0.0);
    }

    @Test
    void shouldReflectRateLimiterMetricsValues() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CircuitBreaker cb = CircuitBreaker.of("testCb2", CircuitBreakerConfig.ofDefaults());
        RateLimiterConfig rlConfig = RateLimiterConfig.custom()
                .limitForPeriod(5)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build();
        RateLimiter rl = RateLimiter.of("testRl2", rlConfig);

        new ResilienceMetricsRegistrar(registry, cb, rl);

        Gauge permitsGauge = findGauge(registry, "resilience4j.ratelimiter.available.permissions", "testRl2");
        assertThat(permitsGauge).isNotNull();
        assertThat(permitsGauge.value()).isEqualTo(5.0);

        rl.acquirePermission();
        assertThat(permitsGauge.value()).isEqualTo(4.0);

        Gauge waitingGauge = findGauge(registry, "resilience4j.ratelimiter.waiting.threads", "testRl2");
        assertThat(waitingGauge).isNotNull();
        assertThat(waitingGauge.value()).isEqualTo(0.0);
    }

    @Test
    void shouldReturnMeterRegistry() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CircuitBreaker cb = CircuitBreaker.of("testCb3", CircuitBreakerConfig.ofDefaults());
        RateLimiter rl = RateLimiter.of("testRl3", RateLimiterConfig.ofDefaults());

        ResilienceMetricsRegistrar registrar = new ResilienceMetricsRegistrar(registry, cb, rl);

        assertThat(registrar.getMeterRegistry()).isSameAs(registry);
    }

    @Test
    void shouldRegisterAdditionalCircuitBreakerGauges() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CircuitBreaker cb = CircuitBreaker.of("primary", CircuitBreakerConfig.ofDefaults());
        RateLimiter rl = RateLimiter.of("testRl4", RateLimiterConfig.ofDefaults());

        ResilienceMetricsRegistrar registrar = new ResilienceMetricsRegistrar(registry, cb, rl);

        CircuitBreaker secondary = CircuitBreaker.of("secondary", CircuitBreakerConfig.ofDefaults());
        registrar.registerCircuitBreakerGauges(secondary);

        assertThat(findGauge(registry, "resilience4j.circuitbreaker.state", "primary")).isNotNull();
        assertThat(findGauge(registry, "resilience4j.circuitbreaker.state", "secondary")).isNotNull();
    }

    @Test
    void shouldRegisterAdditionalRateLimiterGauges() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CircuitBreaker cb = CircuitBreaker.of("testCb5", CircuitBreakerConfig.ofDefaults());
        RateLimiter rl = RateLimiter.of("primaryRl", RateLimiterConfig.ofDefaults());

        ResilienceMetricsRegistrar registrar = new ResilienceMetricsRegistrar(registry, cb, rl);

        RateLimiter secondaryRl = RateLimiter.of("secondaryRl", RateLimiterConfig.ofDefaults());
        registrar.registerRateLimiterGauges(secondaryRl);

        assertThat(findGauge(registry, "resilience4j.ratelimiter.available.permissions", "primaryRl")).isNotNull();
        assertThat(findGauge(registry, "resilience4j.ratelimiter.available.permissions", "secondaryRl")).isNotNull();
    }

    private Gauge findGauge(SimpleMeterRegistry registry, String name, String nameTag) {
        return registry.find(name).tag("name", nameTag).gauge();
    }
}

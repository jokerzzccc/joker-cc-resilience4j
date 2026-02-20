package com.joker.resilience4j.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 为 Factory 管理的 Resilience4j 实例手动注册 Micrometer Gauge。
 *
 * <p>由于 Factory 创建的实例绕过 Registry，{@code TaggedCircuitBreakerMetrics} 等
 * 自动绑定机制无法监控它们。此类为每个实例注册 6 个 CircuitBreaker Gauge
 * 和 2 个 RateLimiter Gauge，确保 Prometheus 可以抓取到完整指标。</p>
 */
public class ResilienceMetricsRegistrar {

    private final MeterRegistry meterRegistry;

    public ResilienceMetricsRegistrar(
            MeterRegistry meterRegistry,
            CircuitBreaker circuitBreaker,
            RateLimiter rateLimiter
    ) {
        this.meterRegistry = meterRegistry;
        registerCircuitBreakerGauges(circuitBreaker);
        registerRateLimiterGauges(rateLimiter);
    }

    public void registerCircuitBreakerGauges(CircuitBreaker cb) {
        String name = cb.getName();

        Gauge.builder("resilience4j.circuitbreaker.state", cb,
                        c -> c.getState().getOrder())
                .tag("name", name)
                .description("CircuitBreaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)")
                .register(meterRegistry);

        Gauge.builder("resilience4j.circuitbreaker.failure.rate", cb,
                        c -> c.getMetrics().getFailureRate())
                .tag("name", name)
                .description("CircuitBreaker failure rate percentage")
                .register(meterRegistry);

        Gauge.builder("resilience4j.circuitbreaker.slow.call.rate", cb,
                        c -> c.getMetrics().getSlowCallRate())
                .tag("name", name)
                .description("CircuitBreaker slow call rate percentage")
                .register(meterRegistry);

        Gauge.builder("resilience4j.circuitbreaker.buffered.calls", cb,
                        c -> c.getMetrics().getNumberOfBufferedCalls())
                .tag("name", name)
                .description("Number of buffered calls in the sliding window")
                .register(meterRegistry);

        Gauge.builder("resilience4j.circuitbreaker.failed.calls", cb,
                        c -> c.getMetrics().getNumberOfFailedCalls())
                .tag("name", name)
                .description("Number of failed calls in the sliding window")
                .register(meterRegistry);

        Gauge.builder("resilience4j.circuitbreaker.not.permitted.calls", cb,
                        c -> c.getMetrics().getNumberOfNotPermittedCalls())
                .tag("name", name)
                .description("Number of not permitted calls")
                .register(meterRegistry);
    }

    public void registerRateLimiterGauges(RateLimiter rl) {
        String name = rl.getName();

        Gauge.builder("resilience4j.ratelimiter.available.permissions", rl,
                        r -> r.getMetrics().getAvailablePermissions())
                .tag("name", name)
                .description("Available permissions for the rate limiter")
                .register(meterRegistry);

        Gauge.builder("resilience4j.ratelimiter.waiting.threads", rl,
                        r -> r.getMetrics().getNumberOfWaitingThreads())
                .tag("name", name)
                .description("Number of threads waiting for permission")
                .register(meterRegistry);
    }

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
}

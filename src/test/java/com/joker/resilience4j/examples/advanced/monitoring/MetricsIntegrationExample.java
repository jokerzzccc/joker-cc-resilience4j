package com.joker.resilience4j.examples.advanced.monitoring;

import com.joker.resilience4j.config.ResilienceMetricsRegistrar;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class MetricsIntegrationExample {

    public static void main(String[] args) {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        CircuitBreaker cb = CircuitBreaker.of("demo-cb", CircuitBreakerConfig.custom()
                .slidingWindowSize(5)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(50)
                .build());
        RateLimiter rl = RateLimiter.of("demo-rl", RateLimiterConfig.custom()
                .limitForPeriod(10)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build());

        ResilienceMetricsRegistrar registrar = new ResilienceMetricsRegistrar(meterRegistry, cb, rl);

        Counter callCounter = Counter.builder("demo.calls.total")
                .tag("component", "demo")
                .register(meterRegistry);

        Timer callTimer = Timer.builder("demo.call.duration")
                .tag("component", "demo")
                .register(meterRegistry);

        cb.onSuccess(100, TimeUnit.MILLISECONDS);
        cb.onSuccess(200, TimeUnit.MILLISECONDS);
        cb.onError(0, TimeUnit.MILLISECONDS, new RuntimeException("fail"));
        callCounter.increment(3);
        callTimer.record(Duration.ofMillis(150));

        rl.acquirePermission();
        rl.acquirePermission();

        Gauge stateGauge = meterRegistry.find("resilience4j.circuitbreaker.state")
                .tag("name", "demo-cb").gauge();
        Gauge failureRateGauge = meterRegistry.find("resilience4j.circuitbreaker.failure.rate")
                .tag("name", "demo-cb").gauge();
        Gauge permitsGauge = meterRegistry.find("resilience4j.ratelimiter.available.permissions")
                .tag("name", "demo-rl").gauge();

        System.out.println("=== Resilience4j Metrics Demo ===");
        System.out.println("CB state=" + (stateGauge != null ? stateGauge.value() : "N/A"));
        System.out.println("CB failureRate=" + (failureRateGauge != null ? failureRateGauge.value() : "N/A"));
        System.out.println("RL availablePermits=" + (permitsGauge != null ? permitsGauge.value() : "N/A"));
        System.out.println("Custom counter=" + callCounter.count());
        System.out.println("Custom timer count=" + callTimer.count()
                + " mean=" + callTimer.mean(TimeUnit.MILLISECONDS) + "ms");
        System.out.println("MeterRegistry=" + registrar.getMeterRegistry().getClass().getSimpleName());
    }
}

package com.joker.resilience4j.controller;

import com.joker.resilience4j.model.ApiResponse;
import com.joker.resilience4j.model.MetricsSnapshot;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

/**
 * 指标快照端点，聚合展示自定义 Counter 和 Timer 的当前值。
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final Counter circuitBreakerCallCounter;
    private final Counter rateLimiterCallCounter;
    private final Timer apiCallTimer;

    public MetricsController(
            Counter circuitBreakerCallCounter,
            Counter rateLimiterCallCounter,
            Timer apiCallTimer
    ) {
        this.circuitBreakerCallCounter = circuitBreakerCallCounter;
        this.rateLimiterCallCounter = rateLimiterCallCounter;
        this.apiCallTimer = apiCallTimer;
    }

    @GetMapping("/snapshot")
    public Mono<ApiResponse<MetricsSnapshot>> snapshot() {
        return Mono.fromSupplier(() -> {
            MetricsSnapshot metricsSnapshot = new MetricsSnapshot(
                    circuitBreakerCallCounter.count(),
                    rateLimiterCallCounter.count(),
                    apiCallTimer.count(),
                    apiCallTimer.totalTime(TimeUnit.MILLISECONDS),
                    apiCallTimer.mean(TimeUnit.MILLISECONDS),
                    apiCallTimer.max(TimeUnit.MILLISECONDS)
            );
            return ApiResponse.success(metricsSnapshot);
        });
    }
}

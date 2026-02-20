package com.joker.resilience4j.controller;

import com.joker.resilience4j.config.CircuitBreakerFactory;
import com.joker.resilience4j.config.RateLimiterFactory;
import com.joker.resilience4j.model.ApiResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 动态配置管理端点，支持运行时更新 CircuitBreaker 和 RateLimiter 的配置参数。
 * 更新操作通过 Factory.update() 创建新实例替换缓存中的旧实例。
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final CircuitBreakerFactory circuitBreakerFactory;
    private final RateLimiterFactory rateLimiterFactory;

    public ConfigController(
            CircuitBreakerFactory circuitBreakerFactory,
            RateLimiterFactory rateLimiterFactory
    ) {
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.rateLimiterFactory = rateLimiterFactory;
    }

    @PostMapping("/circuit-breaker/{name}")
    public Mono<ApiResponse<String>> updateCircuitBreaker(
            @PathVariable String name,
            @RequestParam(defaultValue = "50") float failureRateThreshold,
            @RequestParam(defaultValue = "50") float slowCallRateThreshold,
            @RequestParam(defaultValue = "10") int slidingWindowSize,
            @RequestParam(defaultValue = "5") int minimumNumberOfCalls,
            @RequestParam(defaultValue = "5000") long waitDurationInOpenStateMs
    ) {
        return Mono.fromSupplier(() -> {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .failureRateThreshold(failureRateThreshold)
                    .slowCallRateThreshold(slowCallRateThreshold)
                    .slidingWindowSize(slidingWindowSize)
                    .minimumNumberOfCalls(minimumNumberOfCalls)
                    .waitDurationInOpenState(Duration.ofMillis(waitDurationInOpenStateMs))
                    .build();
            circuitBreakerFactory.update(name, config);
            return ApiResponse.success("CircuitBreaker '" + name + "' updated");
        });
    }

    @GetMapping("/circuit-breaker/{name}")
    public Mono<ApiResponse<Map<String, Object>>> getCircuitBreakerConfig(@PathVariable String name) {
        return Mono.fromSupplier(() -> {
            Optional<CircuitBreaker> found = circuitBreakerFactory.get(name);
            if (found.isEmpty()) {
                return ApiResponse.failure(
                        new com.joker.resilience4j.model.ErrorResponse(
                                "CircuitBreaker not found: " + name, "NotFound", "UNKNOWN"));
            }
            CircuitBreaker cb = found.get();
            CircuitBreakerConfig cfg = cb.getCircuitBreakerConfig();
            Map<String, Object> configMap = Map.of(
                    "name", cb.getName(),
                    "failureRateThreshold", cfg.getFailureRateThreshold(),
                    "slowCallRateThreshold", cfg.getSlowCallRateThreshold(),
                    "slidingWindowSize", cfg.getSlidingWindowSize(),
                    "minimumNumberOfCalls", cfg.getMinimumNumberOfCalls(),
                    "state", cb.getState().name()
            );
            return ApiResponse.success(configMap);
        });
    }

    @PostMapping("/rate-limiter/{name}")
    public Mono<ApiResponse<String>> updateRateLimiter(
            @PathVariable String name,
            @RequestParam(defaultValue = "10") int limitForPeriod,
            @RequestParam(defaultValue = "1000") long limitRefreshPeriodMs,
            @RequestParam(defaultValue = "0") long timeoutDurationMs
    ) {
        return Mono.fromSupplier(() -> {
            RateLimiterConfig config = RateLimiterConfig.custom()
                    .limitForPeriod(limitForPeriod)
                    .limitRefreshPeriod(Duration.ofMillis(limitRefreshPeriodMs))
                    .timeoutDuration(Duration.ofMillis(timeoutDurationMs))
                    .build();
            rateLimiterFactory.update(name, config);
            return ApiResponse.success("RateLimiter '" + name + "' updated");
        });
    }

    @GetMapping("/rate-limiter/{name}")
    public Mono<ApiResponse<Map<String, Object>>> getRateLimiterConfig(@PathVariable String name) {
        return Mono.fromSupplier(() -> {
            Optional<RateLimiter> found = rateLimiterFactory.get(name);
            if (found.isEmpty()) {
                return ApiResponse.failure(
                        new com.joker.resilience4j.model.ErrorResponse(
                                "RateLimiter not found: " + name, "NotFound", "UNKNOWN"));
            }
            RateLimiter rl = found.get();
            RateLimiterConfig cfg = rl.getRateLimiterConfig();
            Map<String, Object> configMap = Map.of(
                    "name", rl.getName(),
                    "limitForPeriod", cfg.getLimitForPeriod(),
                    "limitRefreshPeriodMs", cfg.getLimitRefreshPeriod().toMillis(),
                    "timeoutDurationMs", cfg.getTimeoutDuration().toMillis(),
                    "availablePermissions", rl.getMetrics().getAvailablePermissions()
            );
            return ApiResponse.success(configMap);
        });
    }
}

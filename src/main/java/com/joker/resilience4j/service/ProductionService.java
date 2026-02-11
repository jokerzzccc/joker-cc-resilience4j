package com.joker.resilience4j.service;

import com.joker.resilience4j.config.CircuitBreakerFactory;
import com.joker.resilience4j.config.RateLimiterFactory;
import com.joker.resilience4j.model.ApiResponse;
import com.joker.resilience4j.model.ErrorResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class ProductionService {

    private static final String MODE_SUCCESS = "success";
    private static final String MODE_FAILURE = "failure";
    private static final String MODE_SLOW = "slow";
    private static final String CACHE_KEY = "lastSuccess";

    private final CircuitBreakerFactory circuitBreakerFactory;
    private final RateLimiterFactory rateLimiterFactory;
    private final ExternalApiService externalApiService;
    private final ConcurrentMap<String, String> fallbackCache = new ConcurrentHashMap<>();

    private record CallPlan(Supplier<String> supplier, boolean blocking) {
    }

    public ProductionService(
            CircuitBreakerFactory circuitBreakerFactory,
            RateLimiterFactory rateLimiterFactory,
            ExternalApiService externalApiService
    ) {
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.rateLimiterFactory = rateLimiterFactory;
        this.externalApiService = externalApiService;
    }

    public Mono<ApiResponse<String>> callWithFullProtection(String mode, long delayMs) {
        CircuitBreaker cb = circuitBreakerFactory.get("externalApi")
                .orElseGet(() -> circuitBreakerFactory.create(
                        "externalApi", CircuitBreakerFactory.ConfigTemplate.HYBRID));
        RateLimiter rl = rateLimiterFactory.get("externalApi")
                .orElseGet(() -> rateLimiterFactory.create(
                        "externalApi", RateLimiterFactory.ConfigTemplate.BURST));

        CallPlan plan = selectSupplier(mode, delayMs);
        Mono<String> source = Mono.fromCallable(plan.supplier()::get);
        if (plan.blocking()) {
            source = source.subscribeOn(Schedulers.boundedElastic());
        }

        return source
                .transformDeferred(RateLimiterOperator.of(rl))
                .transformDeferred(CircuitBreakerOperator.of(cb))
                .doOnNext(result -> fallbackCache.put(CACHE_KEY, result))
                .map(ApiResponse::success)
                .onErrorResume(this::multiLevelFallback);
    }

    public Mono<ApiResponse<Map<String, String>>> getCacheSnapshot() {
        return Mono.fromSupplier(() -> ApiResponse.success(Map.copyOf(fallbackCache)));
    }

    public Mono<ApiResponse<String>> clearCache() {
        return Mono.fromSupplier(() -> {
            fallbackCache.clear();
            return ApiResponse.success("cache cleared");
        });
    }

    private Mono<ApiResponse<String>> multiLevelFallback(Throwable throwable) {
        String cached = fallbackCache.get(CACHE_KEY);
        if (cached != null) {
            return Mono.just(ApiResponse.success("cached:" + cached));
        }
        if (throwable instanceof CallNotPermittedException
                || throwable instanceof RequestNotPermitted) {
            return Mono.just(ApiResponse.success("static-fallback"));
        }
        return Mono.just(ApiResponse.failure(ErrorResponse.from(throwable, "DEGRADED")));
    }

    private CallPlan selectSupplier(String mode, long delayMs) {
        String normalized = mode == null ? MODE_SUCCESS : mode.toLowerCase(Locale.ROOT).trim();
        return switch (normalized) {
            case MODE_FAILURE -> new CallPlan(externalApiService::callFailure, false);
            case MODE_SLOW -> new CallPlan(
                    () -> externalApiService.callSlow(Duration.ofMillis(Math.max(0, delayMs))),
                    true
            );
            default -> new CallPlan(externalApiService::callSuccess, false);
        };
    }
}

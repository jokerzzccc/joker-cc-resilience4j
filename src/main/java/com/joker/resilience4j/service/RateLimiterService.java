package com.joker.resilience4j.service;

import com.joker.resilience4j.model.ApiResponse;
import com.joker.resilience4j.model.ErrorResponse;
import com.joker.resilience4j.model.RateLimiterStatus;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class RateLimiterService {

    private static final String MODE_SUCCESS = "success";
    private static final String MODE_FAILURE = "failure";
    private static final String MODE_SLOW = "slow";

    private final RateLimiter rateLimiter;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final ExternalApiService externalApiService;

    private record CallPlan(Supplier<String> supplier, boolean blocking) {
    }

    public RateLimiterService(
            RateLimiter rateLimiter,
            RateLimiterRegistry rateLimiterRegistry,
            ExternalApiService externalApiService
    ) {
        this.rateLimiter = rateLimiter;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.externalApiService = externalApiService;
    }

    public Mono<ApiResponse<String>> callExternal(String mode, long delayMs) {
        return callWithRateLimiter(rateLimiter, mode, delayMs, false);
    }

    public Mono<ApiResponse<String>> callExternalWithFallback(String mode, long delayMs) {
        return callWithRateLimiter(rateLimiter, mode, delayMs, true);
    }

    public Mono<ApiResponse<RateLimiterStatus>> getStatus() {
        return getStatus(rateLimiter.getName());
    }

    public Mono<ApiResponse<RateLimiterStatus>> getStatus(String name) {
        return Mono.fromSupplier(() -> {
            java.util.Optional<RateLimiter> resolved = rateLimiterRegistry.find(name);
            if (resolved.isEmpty()) {
                return ApiResponse.failure(new ErrorResponse(
                        "RateLimiter not found: " + name,
                        "NotFound",
                        "UNKNOWN"
                ));
            }
            RateLimiter selected = resolved.get();
            RateLimiter.Metrics metrics = selected.getMetrics();
            RateLimiterStatus status = new RateLimiterStatus(
                    selected.getName(),
                    metrics.getAvailablePermissions(),
                    metrics.getNumberOfWaitingThreads()
            );
            return ApiResponse.success(status);
        });
    }

    public Mono<ApiResponse<Set<String>>> listRateLimiters() {
        return Mono.fromSupplier(() -> {
            Set<String> names = new TreeSet<>();
            rateLimiterRegistry.getAllRateLimiters()
                    .forEach(rl -> names.add(rl.getName()));
            return ApiResponse.success(names);
        });
    }

    private Mono<ApiResponse<String>> callWithRateLimiter(
            RateLimiter selected,
            String mode,
            long delayMs,
            boolean useFallback
    ) {
        CallPlan plan = selectSupplier(mode, delayMs);
        Mono<String> source = Mono.fromCallable(plan.supplier()::get);
        if (plan.blocking()) {
            source = source.subscribeOn(Schedulers.boundedElastic());
        }

        Mono<String> guarded = source.transformDeferred(RateLimiterOperator.of(selected));
        if (useFallback) {
            return guarded
                    .map(ApiResponse::success)
                    .onErrorResume(throwable -> Mono.just(ApiResponse.success(
                            "fallback-" + selected.getName()
                    )));
        }

        return guarded
                .map(ApiResponse::success)
                .onErrorResume(throwable -> Mono.just(ApiResponse.failure(
                        ErrorResponse.from(throwable, "RATE_LIMITED")
                )));
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

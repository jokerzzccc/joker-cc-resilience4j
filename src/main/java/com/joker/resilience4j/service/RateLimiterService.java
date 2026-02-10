package com.joker.resilience4j.service;

import com.joker.resilience4j.config.RateLimiterFactory;
import com.joker.resilience4j.model.ApiResponse;
import com.joker.resilience4j.model.CombinedRateLimiterStatus;
import com.joker.resilience4j.model.ErrorResponse;
import com.joker.resilience4j.model.RateLimiterStatus;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class RateLimiterService {

    private static final String MODE_SUCCESS = "success";
    private static final String MODE_FAILURE = "failure";
    private static final String MODE_SLOW = "slow";

    private final RateLimiterFactory rateLimiterFactory;
    private final RateLimiter rateLimiter;
    private final ExternalApiService externalApiService;

    private record CallPlan(Supplier<String> supplier, boolean blocking) {
    }

    public RateLimiterService(
            RateLimiterFactory rateLimiterFactory,
            RateLimiter rateLimiter,
            ExternalApiService externalApiService
    ) {
        this.rateLimiterFactory = rateLimiterFactory;
        this.rateLimiter = rateLimiter;
        this.externalApiService = externalApiService;
    }

    public Mono<ApiResponse<String>> callExternal(String mode, long delayMs) {
        return callWithRateLimiter(rateLimiter.getName(), mode, delayMs, false);
    }

    public Mono<ApiResponse<String>> callExternalWithFallback(String mode, long delayMs) {
        return callWithRateLimiter(rateLimiter.getName(), mode, delayMs, true);
    }

    public Mono<ApiResponse<String>> callExternalWithName(String name, String mode, long delayMs) {
        return callWithRateLimiter(name, mode, delayMs, false);
    }

    public Mono<ApiResponse<String>> callExternalWithCombo(
            String primaryName,
            String secondaryName,
            String mode,
            long delayMs
    ) {
        RateLimiter primary = resolveRateLimiter(primaryName);
        RateLimiter secondary = resolveRateLimiter(secondaryName);
        CallPlan plan = selectSupplier(mode, delayMs);
        Mono<String> source = Mono.fromCallable(plan.supplier()::get);
        if (plan.blocking()) {
            source = source.subscribeOn(Schedulers.boundedElastic());
        }

        return source
                .transformDeferred(RateLimiterOperator.of(primary))
                .transformDeferred(RateLimiterOperator.of(secondary))
                .map(ApiResponse::success)
                .onErrorResume(throwable -> Mono.just(ApiResponse.failure(
                        ErrorResponse.from(throwable, "RATE_LIMITED")
                )));
    }

    public Mono<ApiResponse<RateLimiterStatus>> getStatus() {
        return getStatus(rateLimiter.getName());
    }

    public Mono<ApiResponse<RateLimiterStatus>> getStatus(String name) {
        return Mono.fromSupplier(() -> {
            Optional<RateLimiter> resolved = rateLimiterFactory.get(name);
            if (resolved.isEmpty()) {
                return ApiResponse.failure(new ErrorResponse(
                        "RateLimiter not found: " + name,
                        "NotFound",
                        "UNKNOWN"
                ));
            }
            return ApiResponse.success(toStatus(resolved.get()));
        });
    }

    public Mono<ApiResponse<CombinedRateLimiterStatus>> getCombinedStatus(
            String primaryName,
            String secondaryName
    ) {
        return Mono.fromSupplier(() -> {
            RateLimiter primary = resolveRateLimiter(primaryName);
            RateLimiter secondary = resolveRateLimiter(secondaryName);
            RateLimiterStatus primaryStatus = toStatus(primary);
            RateLimiterStatus secondaryStatus = toStatus(secondary);
            return ApiResponse.success(new CombinedRateLimiterStatus(primaryStatus, secondaryStatus));
        });
    }

    public Mono<ApiResponse<Set<String>>> listRateLimiters() {
        return Mono.fromSupplier(() -> ApiResponse.success(rateLimiterFactory.listNames()));
    }

    private Mono<ApiResponse<String>> callWithRateLimiter(
            String name,
            String mode,
            long delayMs,
            boolean useFallback
    ) {
        RateLimiter selected = resolveRateLimiter(name);
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

    private RateLimiterStatus toStatus(RateLimiter rateLimiter) {
        RateLimiter.Metrics metrics = rateLimiter.getMetrics();
        return new RateLimiterStatus(
                rateLimiter.getName(),
                metrics.getAvailablePermissions(),
                metrics.getNumberOfWaitingThreads()
        );
    }

    private RateLimiter resolveRateLimiter(String name) {
        String resolvedName = name == null || name.isBlank() ? rateLimiter.getName() : name.trim();
        return rateLimiterFactory.get(resolvedName)
                .orElseGet(() -> rateLimiterFactory.create(
                        resolvedName,
                        RateLimiterFactory.ConfigTemplate.BURST
                ));
    }
}

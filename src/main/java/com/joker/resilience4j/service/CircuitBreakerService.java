package com.joker.resilience4j.service;

import com.joker.resilience4j.config.CircuitBreakerFactory;
import com.joker.resilience4j.model.ApiResponse;
import com.joker.resilience4j.model.CombinedCircuitBreakerStatus;
import com.joker.resilience4j.model.CircuitBreakerStatus;
import com.joker.resilience4j.model.ErrorResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 熔断器业务逻辑服务，通过 {@code transformDeferred(CircuitBreakerOperator.of(...))}
 * 将外部调用纳入熔断保护。
 *
 * <p>支持单熔断器调用、带 fallback 调用、命名调用、双熔断器组合调用，
 * 以及熔断器状态查询。所有方法返回 {@link Mono}，保持全链路响应式。</p>
 */
@Service
public class CircuitBreakerService {

    private static final String MODE_SUCCESS = "success";
    private static final String MODE_FAILURE = "failure";
    private static final String MODE_SLOW = "slow";

    private final CircuitBreakerFactory circuitBreakerFactory;
    private final CircuitBreaker circuitBreaker;
    private final ExternalApiService externalApiService;

    private record CallPlan(Supplier<String> supplier, boolean blocking) {
    }

    public CircuitBreakerService(
            CircuitBreakerFactory circuitBreakerFactory,
            CircuitBreaker circuitBreaker,
            ExternalApiService externalApiService
    ) {
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.circuitBreaker = circuitBreaker;
        this.externalApiService = externalApiService;
    }

    public Mono<ApiResponse<String>> callExternal(String mode, long delayMs) {
        return callWithCircuitBreaker(circuitBreaker.getName(), mode, delayMs, false);
    }

    public Mono<ApiResponse<String>> callExternalWithFallback(String mode, long delayMs) {
        return callWithCircuitBreaker(circuitBreaker.getName(), mode, delayMs, true);
    }

    public Mono<ApiResponse<String>> callExternalWithName(String name, String mode, long delayMs) {
        return callWithCircuitBreaker(name, mode, delayMs, false);
    }

    public Mono<ApiResponse<String>> callExternalWithCombo(
            String primaryName,
            String secondaryName,
            String mode,
            long delayMs
    ) {
        CircuitBreaker primary = resolveCircuitBreaker(primaryName);
        CircuitBreaker secondary = resolveCircuitBreaker(secondaryName);
        CallPlan plan = selectSupplier(mode, delayMs);
        Mono<String> source = Mono.fromCallable(plan.supplier()::get);
        if (plan.blocking()) {
            source = source.subscribeOn(Schedulers.boundedElastic());
        }

        return source
                .transformDeferred(CircuitBreakerOperator.of(primary))
                .transformDeferred(CircuitBreakerOperator.of(secondary))
                .map(ApiResponse::success)
                .onErrorResume(throwable -> Mono.just(ApiResponse.failure(
                        toErrorResponse(throwable, primary, secondary)
                )));
    }

    public Mono<ApiResponse<CircuitBreakerStatus>> getStatus() {
        return getStatus(circuitBreaker.getName());
    }

    public Mono<ApiResponse<CircuitBreakerStatus>> getStatus(String name) {
        return Mono.fromSupplier(() -> {
            Optional<CircuitBreaker> resolved = circuitBreakerFactory.get(name);
            if (resolved.isEmpty()) {
                return ApiResponse.failure(new ErrorResponse(
                        "CircuitBreaker not found: " + name,
                        "NotFound",
                        "UNKNOWN"
                ));
            }
            CircuitBreaker selected = resolved.get();
            CircuitBreaker.Metrics metrics = selected.getMetrics();
            CircuitBreakerStatus status = new CircuitBreakerStatus(
                    selected.getName(),
                    selected.getState().name(),
                    metrics.getFailureRate(),
                    metrics.getSlowCallRate(),
                    metrics.getNumberOfBufferedCalls(),
                    metrics.getNumberOfFailedCalls(),
                    metrics.getNumberOfSlowCalls(),
                    metrics.getNumberOfNotPermittedCalls()
            );
            return ApiResponse.success(status);
        });
    }

    public Mono<ApiResponse<CombinedCircuitBreakerStatus>> getCombinedStatus(
            String primaryName,
            String secondaryName
    ) {
        return Mono.fromSupplier(() -> {
            CircuitBreaker primary = resolveCircuitBreaker(primaryName);
            CircuitBreaker secondary = resolveCircuitBreaker(secondaryName);
            CircuitBreakerStatus primaryStatus = toStatus(primary);
            CircuitBreakerStatus secondaryStatus = toStatus(secondary);
            return ApiResponse.success(new CombinedCircuitBreakerStatus(primaryStatus, secondaryStatus));
        });
    }

    public Mono<ApiResponse<Set<String>>> listCircuitBreakers() {
        return Mono.fromSupplier(() -> ApiResponse.success(circuitBreakerFactory.listNames()));
    }

    private Mono<ApiResponse<String>> callWithCircuitBreaker(
            String name,
            String mode,
            long delayMs,
            boolean useFallback
    ) {
        CircuitBreaker selected = resolveCircuitBreaker(name);
        CallPlan plan = selectSupplier(mode, delayMs);
        Mono<String> source = Mono.fromCallable(plan.supplier()::get);
        if (plan.blocking()) {
            source = source.subscribeOn(Schedulers.boundedElastic());
        }

        Mono<String> guarded = source.transformDeferred(CircuitBreakerOperator.of(selected));
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
                        ErrorResponse.from(throwable, selected.getState().name())
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

    private CircuitBreakerStatus toStatus(CircuitBreaker circuitBreaker) {
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        return new CircuitBreakerStatus(
                circuitBreaker.getName(),
                circuitBreaker.getState().name(),
                metrics.getFailureRate(),
                metrics.getSlowCallRate(),
                metrics.getNumberOfBufferedCalls(),
                metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfSlowCalls(),
                metrics.getNumberOfNotPermittedCalls()
        );
    }

    private CircuitBreaker resolveCircuitBreaker(String name) {
        String resolvedName = name == null || name.isBlank() ? circuitBreaker.getName() : name.trim();
        return circuitBreakerFactory.get(resolvedName)
                .orElseGet(() -> circuitBreakerFactory.create(
                        resolvedName,
                        CircuitBreakerFactory.ConfigTemplate.HYBRID
                ));
    }

    private ErrorResponse toErrorResponse(Throwable throwable, CircuitBreaker primary, CircuitBreaker secondary) {
        if (throwable instanceof CallNotPermittedException) {
            String state = primary.getState().name();
            if (!CircuitBreaker.State.OPEN.name().equals(state)) {
                state = secondary.getState().name();
            }
            return ErrorResponse.from(throwable, state);
        }
        return ErrorResponse.from(throwable, primary.getState().name());
    }
}

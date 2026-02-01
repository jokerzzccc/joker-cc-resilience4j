package com.joker.resilience4j.service;

import com.joker.resilience4j.model.ApiResponse;
import com.joker.resilience4j.model.CircuitBreakerStatus;
import com.joker.resilience4j.model.ErrorResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class CircuitBreakerService {

    private static final String MODE_SUCCESS = "success";
    private static final String MODE_FAILURE = "failure";
    private static final String MODE_SLOW = "slow";

    private final CircuitBreaker circuitBreaker;
    private final ExternalApiService externalApiService;

    private record CallPlan(Supplier<String> supplier, boolean blocking) {
    }

    public CircuitBreakerService(CircuitBreaker circuitBreaker, ExternalApiService externalApiService) {
        this.circuitBreaker = circuitBreaker;
        this.externalApiService = externalApiService;
    }

    public Mono<ApiResponse<String>> callExternal(String mode, long delayMs) {
        CallPlan plan = selectSupplier(mode, delayMs);
        Mono<String> source = Mono.fromCallable(plan.supplier()::get);
        if (plan.blocking()) {
            source = source.subscribeOn(Schedulers.boundedElastic());
        }

        return source
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .map(ApiResponse::success)
                .onErrorResume(throwable -> Mono.just(ApiResponse.failure(
                        ErrorResponse.from(throwable, circuitBreaker.getState().name())
                )));
    }

    public ApiResponse<CircuitBreakerStatus> getStatus() {
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        CircuitBreakerStatus status = new CircuitBreakerStatus(
                circuitBreaker.getName(),
                circuitBreaker.getState().name(),
                metrics.getFailureRate(),
                metrics.getSlowCallRate(),
                metrics.getNumberOfBufferedCalls(),
                metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfSlowCalls(),
                metrics.getNumberOfNotPermittedCalls()
        );
        return ApiResponse.success(status);
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

package com.joker.resilience4j.service;

import com.joker.resilience4j.model.ApiResponse;
import com.joker.resilience4j.model.CircuitBreakerStatus;
import com.joker.resilience4j.model.ErrorResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
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

    public CircuitBreakerService(CircuitBreaker circuitBreaker, ExternalApiService externalApiService) {
        this.circuitBreaker = circuitBreaker;
        this.externalApiService = externalApiService;
    }

    public Mono<ApiResponse<String>> callExternal(String mode, long delayMs) {
        Supplier<String> supplier = selectSupplier(mode, delayMs);
        Supplier<String> protectedSupplier = circuitBreaker.decorateSupplier(supplier);

        return Mono.fromSupplier(protectedSupplier)
                .subscribeOn(Schedulers.boundedElastic())
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

    private Supplier<String> selectSupplier(String mode, long delayMs) {
        String normalized = mode == null ? MODE_SUCCESS : mode.toLowerCase(Locale.ROOT).trim();
        return switch (normalized) {
            case MODE_FAILURE -> externalApiService::callFailure;
            case MODE_SLOW -> () -> externalApiService.callSlow(Duration.ofMillis(Math.max(0, delayMs)));
            default -> externalApiService::callSuccess;
        };
    }
}

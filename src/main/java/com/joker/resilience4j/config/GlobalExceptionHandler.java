package com.joker.resilience4j.config;

import com.joker.resilience4j.model.ApiResponse;
import com.joker.resilience4j.model.ErrorResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import reactor.core.publisher.Mono;

/**
 * 全局异常处理器，将 Resilience4j 异常映射为标准 HTTP 状态码。
 *
 * <ul>
 *   <li>{@link CallNotPermittedException} → 503 Service Unavailable</li>
 *   <li>{@link RequestNotPermitted} → 429 Too Many Requests</li>
 *   <li>其他异常 → 500 Internal Server Error</li>
 * </ul>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CallNotPermittedException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ResponseBody
    public Mono<ApiResponse<Void>> handleCircuitBreakerOpen(CallNotPermittedException ex) {
        log.warn("CircuitBreaker open: {}", ex.getMessage());
        return Mono.just(ApiResponse.failure(
                ErrorResponse.from(ex, "OPEN")
        ));
    }

    @ExceptionHandler(RequestNotPermitted.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    @ResponseBody
    public Mono<ApiResponse<Void>> handleRateLimited(RequestNotPermitted ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        return Mono.just(ApiResponse.failure(
                ErrorResponse.from(ex, "RATE_LIMITED")
        ));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public Mono<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return Mono.just(ApiResponse.failure(
                ErrorResponse.from(ex, "ERROR")
        ));
    }
}

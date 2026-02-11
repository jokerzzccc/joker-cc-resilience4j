package com.joker.resilience4j.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldHandleCircuitBreakerOpenException() {
        CircuitBreaker cb = CircuitBreaker.of("test", CircuitBreakerConfig.ofDefaults());
        cb.transitionToOpenState();
        CallNotPermittedException ex = CallNotPermittedException.createCallNotPermittedException(cb);

        StepVerifier.create(handler.handleCircuitBreakerOpen(ex))
                .assertNext(response -> {
                    assertThat(response.success()).isFalse();
                    assertThat(response.error()).isNotNull();
                    assertThat(response.error().componentState()).isEqualTo("OPEN");
                    assertThat(response.error().exception()).isEqualTo("CallNotPermittedException");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleRateLimitedException() {
        RateLimiter rl = RateLimiter.of("test", RateLimiterConfig.ofDefaults());
        RequestNotPermitted ex = RequestNotPermitted.createRequestNotPermitted(rl);

        StepVerifier.create(handler.handleRateLimited(ex))
                .assertNext(response -> {
                    assertThat(response.success()).isFalse();
                    assertThat(response.error()).isNotNull();
                    assertThat(response.error().componentState()).isEqualTo("RATE_LIMITED");
                    assertThat(response.error().exception()).isEqualTo("RequestNotPermitted");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleGenericException() {
        Exception ex = new RuntimeException("unexpected error");

        StepVerifier.create(handler.handleGeneric(ex))
                .assertNext(response -> {
                    assertThat(response.success()).isFalse();
                    assertThat(response.error()).isNotNull();
                    assertThat(response.error().componentState()).isEqualTo("ERROR");
                    assertThat(response.error().message()).isEqualTo("unexpected error");
                })
                .verifyComplete();
    }
}

package com.joker.resilience4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.joker.resilience4j.model.ApiResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CircuitBreakerServiceTest {

    @Test
    void shouldReturnSuccessForHealthyCall() {
        CircuitBreaker circuitBreaker = CircuitBreaker.of(
                "successTest",
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(4)
                        .minimumNumberOfCalls(2)
                        .waitDurationInOpenState(Duration.ofSeconds(1))
                        .build()
        );

        CircuitBreakerService service = new CircuitBreakerService(circuitBreaker, new ExternalApiService());

        Mono<ApiResponse<String>> result = service.callExternal("success", 0);

        StepVerifier.create(result)
                .expectNextMatches(response ->
                        response.success()
                                && response.data() != null
                                && response.data().equals("external-api-success"))
                .verifyComplete();

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldOpenAfterConsecutiveFailures() {
        CircuitBreaker circuitBreaker = CircuitBreaker.of(
                "failureTest",
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(2)
                        .minimumNumberOfCalls(2)
                        .waitDurationInOpenState(Duration.ofSeconds(1))
                        .build()
        );

        CircuitBreakerService service = new CircuitBreakerService(circuitBreaker, new ExternalApiService());

        StepVerifier.create(service.callExternal("failure", 0))
                .expectNextMatches(response -> !response.success() && response.error() != null)
                .verifyComplete();

        StepVerifier.create(service.callExternal("failure", 0))
                .expectNextMatches(response -> !response.success() && response.error() != null)
                .verifyComplete();

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void shouldRecoverAfterOpenStateWait() {
        CircuitBreaker circuitBreaker = CircuitBreaker.of(
                "recoveryTest",
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(2)
                        .minimumNumberOfCalls(2)
                        .permittedNumberOfCallsInHalfOpenState(1)
                        .waitDurationInOpenState(Duration.ofMillis(150))
                        .build()
        );

        CircuitBreakerService service = new CircuitBreakerService(circuitBreaker, new ExternalApiService());

        StepVerifier.create(service.callExternal("failure", 0))
                .expectNextMatches(response -> !response.success() && response.error() != null)
                .verifyComplete();

        StepVerifier.create(service.callExternal("failure", 0))
                .expectNextMatches(response -> !response.success() && response.error() != null)
                .verifyComplete();

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        circuitBreaker.transitionToHalfOpenState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        StepVerifier.create(service.callExternal("success", 0))
                .expectNextMatches(response -> response.success() && response.data() != null)
                .verifyComplete();

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldHandleSlowMode() {
        CircuitBreaker circuitBreaker = CircuitBreaker.of(
                "slowTest",
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .failureRateThreshold(100)
                        .slowCallRateThreshold(100)
                        .slidingWindowSize(4)
                        .minimumNumberOfCalls(2)
                        .build()
        );

        CircuitBreakerService service = new CircuitBreakerService(circuitBreaker, new ExternalApiService());

        StepVerifier.create(service.callExternal("slow", 0))
                .expectNextMatches(response -> response.success() && response.data() != null)
                .verifyComplete();
    }

    @Test
    void shouldDefaultToSuccessWhenModeUnknown() {
        CircuitBreaker circuitBreaker = CircuitBreaker.of(
                "defaultTest",
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(2)
                        .minimumNumberOfCalls(2)
                        .build()
        );

        CircuitBreakerService service = new CircuitBreakerService(circuitBreaker, new ExternalApiService());

        StepVerifier.create(service.callExternal("unknown-mode", 0))
                .expectNextMatches(response -> response.success() && response.data() != null)
                .verifyComplete();
    }

    @Test
    void shouldReturnStatus() {
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("statusTest");
        CircuitBreakerService service = new CircuitBreakerService(circuitBreaker, new ExternalApiService());

        ApiResponse<?> response = service.getStatus();

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isNotNull();
    }
}

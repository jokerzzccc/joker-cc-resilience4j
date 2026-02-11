package com.joker.resilience4j.examples.production;

import static org.assertj.core.api.Assertions.assertThat;

import com.joker.resilience4j.config.CircuitBreakerFactory;
import com.joker.resilience4j.config.RateLimiterFactory;
import com.joker.resilience4j.model.ApiResponse;
import com.joker.resilience4j.model.ErrorResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Production patterns demonstration.
 *
 * Demonstrates:
 * 1. Combined CircuitBreaker + RateLimiter protection
 * 2. Multi-level fallback (cache -> static -> error)
 * 3. Dynamic configuration updates
 * 4. Microservice / third-party API / database protection patterns
 */
class ProductionPatternsExample {

    // --- Pattern 1: Combined CB + RL Protection ---

    @Test
    void combinedProtection() {
        CircuitBreakerFactory cbFactory = new CircuitBreakerFactory(CircuitBreakerConfig.ofDefaults());
        RateLimiterFactory rlFactory = new RateLimiterFactory(RateLimiterConfig.ofDefaults());
        CircuitBreaker cb = cbFactory.create("paymentService", CircuitBreakerFactory.ConfigTemplate.FAST_FAIL);
        RateLimiter rl = rlFactory.create("paymentService", RateLimiterFactory.ConfigTemplate.STRICT);

        // RateLimiter first (reject early), then CircuitBreaker (protect downstream)
        Mono<String> protectedCall = Mono.fromSupplier(() -> "payment-ok")
                .transformDeferred(RateLimiterOperator.of(rl))
                .transformDeferred(CircuitBreakerOperator.of(cb));

        StepVerifier.create(protectedCall)
                .expectNext("payment-ok")
                .verifyComplete();
    }

    // --- Pattern 2: Multi-Level Fallback ---

    @Test
    void multiLevelFallback() {
        ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();
        cache.put("product-123", "cached-product-data");

        CircuitBreaker cb = CircuitBreaker.of("catalog", CircuitBreakerConfig.ofDefaults());
        cb.transitionToOpenState();

        Mono<String> call = Mono.fromSupplier(() -> "live-product-data")
                .transformDeferred(CircuitBreakerOperator.of(cb))
                .onErrorResume(throwable -> {
                    // Level 1: cache
                    String cached = cache.get("product-123");
                    if (cached != null) {
                        return Mono.just("cached:" + cached);
                    }
                    // Level 2: static fallback
                    if (throwable instanceof CallNotPermittedException
                            || throwable instanceof RequestNotPermitted) {
                        return Mono.just("static-fallback");
                    }
                    // Level 3: error
                    return Mono.error(throwable);
                });

        StepVerifier.create(call)
                .expectNext("cached:cached-product-data")
                .verifyComplete();
    }

    // --- Pattern 3: Dynamic Configuration Update ---

    @Test
    void dynamicConfigUpdate() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(CircuitBreakerConfig.ofDefaults());
        factory.create("orderService", CircuitBreakerFactory.ConfigTemplate.HYBRID);

        // Runtime update: tighten failure threshold
        CircuitBreakerConfig strictConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(20)
                .slidingWindowSize(50)
                .minimumNumberOfCalls(10)
                .build();
        CircuitBreaker updated = factory.update("orderService", strictConfig);

        assertThat(updated.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(20.0f);
        assertThat(updated.getCircuitBreakerConfig().getSlidingWindowSize()).isEqualTo(50);
    }

    // --- Pattern 4: Microservice Call Protection ---

    @Test
    void microserviceProtection() {
        CircuitBreaker cb = CircuitBreaker.of("userService",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slowCallRateThreshold(80)
                        .slowCallDurationThreshold(Duration.ofMillis(500))
                        .slidingWindowSize(20)
                        .minimumNumberOfCalls(5)
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .build());

        RateLimiter rl = RateLimiter.of("userService",
                RateLimiterConfig.custom()
                        .limitForPeriod(100)
                        .limitRefreshPeriod(Duration.ofSeconds(1))
                        .timeoutDuration(Duration.ZERO)
                        .build());

        Mono<ApiResponse<String>> result = Mono.fromSupplier(() -> "user-data")
                .transformDeferred(RateLimiterOperator.of(rl))
                .transformDeferred(CircuitBreakerOperator.of(cb))
                .map(ApiResponse::success)
                .onErrorResume(e -> Mono.just(ApiResponse.failure(
                        ErrorResponse.from(e, cb.getState().name()))));

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).isEqualTo("user-data");
                })
                .verifyComplete();
    }

    // --- Pattern 5: Third-Party API Protection ---

    @Test
    void thirdPartyApiProtection() {
        CircuitBreaker cb = CircuitBreaker.of("weatherApi",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(30)
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(3)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .build());

        RateLimiter rl = RateLimiter.of("weatherApi",
                RateLimiterConfig.custom()
                        .limitForPeriod(10)
                        .limitRefreshPeriod(Duration.ofSeconds(1))
                        .timeoutDuration(Duration.ZERO)
                        .build());

        Mono<ApiResponse<String>> result = Mono.fromSupplier(() -> "sunny 25C")
                .transformDeferred(RateLimiterOperator.of(rl))
                .transformDeferred(CircuitBreakerOperator.of(cb))
                .map(ApiResponse::success)
                .onErrorResume(e -> Mono.just(ApiResponse.success("weather unavailable")));

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).isEqualTo("sunny 25C");
                })
                .verifyComplete();
    }

    // --- Pattern 6: Database Access Protection ---

    @Test
    void databaseProtection() {
        CircuitBreaker cb = CircuitBreaker.of("database",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .waitDurationInOpenState(Duration.ofSeconds(5))
                        .build());

        Mono<ApiResponse<String>> result = Mono.fromSupplier(() -> "db-row-data")
                .transformDeferred(CircuitBreakerOperator.of(cb))
                .map(ApiResponse::success)
                .onErrorResume(e -> Mono.just(ApiResponse.failure(
                        ErrorResponse.from(e, cb.getState().name()))));

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).isEqualTo("db-row-data");
                })
                .verifyComplete();
    }
}

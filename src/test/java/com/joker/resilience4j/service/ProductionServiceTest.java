package com.joker.resilience4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.joker.resilience4j.config.CircuitBreakerFactory;
import com.joker.resilience4j.config.RateLimiterFactory;
import com.joker.resilience4j.model.ApiResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ProductionServiceTest {

    private CircuitBreakerFactory cbFactory;
    private RateLimiterFactory rlFactory;
    private ProductionService service;

    @BeforeEach
    void setUp() {
        cbFactory = new CircuitBreakerFactory(
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(2)
                        .minimumNumberOfCalls(2)
                        .waitDurationInOpenState(Duration.ofSeconds(60))
                        .build()
        );
        rlFactory = new RateLimiterFactory(
                RateLimiterConfig.custom()
                        .limitForPeriod(10)
                        .limitRefreshPeriod(Duration.ofSeconds(1))
                        .timeoutDuration(Duration.ZERO)
                        .build()
        );
        cbFactory.create("externalApi", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        rlFactory.create("externalApi", RateLimiterFactory.ConfigTemplate.BURST);
        service = new ProductionService(cbFactory, rlFactory, new ExternalApiService());
    }

    @Test
    void shouldReturnSuccessWithFullProtection() {
        StepVerifier.create(service.callWithFullProtection("success", 0))
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).isEqualTo("external-api-success");
                })
                .verifyComplete();
    }

    @Test
    void shouldCacheSuccessfulResult() {
        StepVerifier.create(service.callWithFullProtection("success", 0))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(service.getCacheSnapshot())
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).containsKey("lastSuccess");
                    assertThat(response.data().get("lastSuccess")).isEqualTo("external-api-success");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnCachedFallbackWhenCircuitBreakerOpen() {
        // First call succeeds and caches
        StepVerifier.create(service.callWithFullProtection("success", 0))
                .expectNextCount(1)
                .verifyComplete();

        // Trigger failures to open circuit breaker
        StepVerifier.create(service.callWithFullProtection("failure", 0))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(service.callWithFullProtection("failure", 0))
                .expectNextCount(1)
                .verifyComplete();

        CircuitBreaker cb = cbFactory.get("externalApi").orElseThrow();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Next call should return cached fallback
        StepVerifier.create(service.callWithFullProtection("success", 0))
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).startsWith("cached:");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnStaticFallbackWhenCircuitBreakerOpenAndNoCache() {
        // Trigger failures to open circuit breaker (no prior success cached)
        StepVerifier.create(service.callWithFullProtection("failure", 0))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(service.callWithFullProtection("failure", 0))
                .expectNextCount(1)
                .verifyComplete();

        CircuitBreaker cb = cbFactory.get("externalApi").orElseThrow();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // No cache available -> static fallback
        StepVerifier.create(service.callWithFullProtection("success", 0))
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).isEqualTo("static-fallback");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnErrorFallbackForNonResilienceException() {
        // Failure mode throws IllegalStateException (not CB/RL exception)
        // With circuit breaker closed, the error passes through as-is
        StepVerifier.create(service.callWithFullProtection("failure", 0))
                .assertNext(response -> {
                    assertThat(response.success()).isFalse();
                    assertThat(response.error()).isNotNull();
                    assertThat(response.error().componentState()).isEqualTo("DEGRADED");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnStaticFallbackWhenRateLimited() {
        // Use a strict rate limiter that quickly exhausts permits
        RateLimiterFactory strictRlFactory = new RateLimiterFactory(
                RateLimiterConfig.custom()
                        .limitForPeriod(1)
                        .limitRefreshPeriod(Duration.ofSeconds(10))
                        .timeoutDuration(Duration.ZERO)
                        .build()
        );
        strictRlFactory.create("externalApi", RateLimiterFactory.ConfigTemplate.BURST);
        CircuitBreakerFactory strictCbFactory = new CircuitBreakerFactory(CircuitBreakerConfig.ofDefaults());
        strictCbFactory.create("externalApi", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        ProductionService strictService = new ProductionService(
                strictCbFactory, strictRlFactory, new ExternalApiService());

        // First call uses the one permit
        StepVerifier.create(strictService.callWithFullProtection("success", 0))
                .assertNext(response -> assertThat(response.success()).isTrue())
                .verifyComplete();

        // Second call should be rate limited -> cached fallback (cache is set from first call)
        StepVerifier.create(strictService.callWithFullProtection("success", 0))
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).startsWith("cached:");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnStaticFallbackWhenRateLimitedAndNoCache() {
        // Rate limiter with zero permits available immediately
        RateLimiterFactory strictRlFactory = new RateLimiterFactory(
                RateLimiterConfig.custom()
                        .limitForPeriod(1)
                        .limitRefreshPeriod(Duration.ofSeconds(10))
                        .timeoutDuration(Duration.ZERO)
                        .build()
        );
        strictRlFactory.create("externalApi", RateLimiterFactory.ConfigTemplate.BURST);
        CircuitBreakerFactory strictCbFactory = new CircuitBreakerFactory(CircuitBreakerConfig.ofDefaults());
        strictCbFactory.create("externalApi", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        ProductionService strictService = new ProductionService(
                strictCbFactory, strictRlFactory, new ExternalApiService());

        // First call uses the one permit
        StepVerifier.create(strictService.callWithFullProtection("success", 0))
                .expectNextCount(1)
                .verifyComplete();

        // Clear cache so no cached fallback is available
        StepVerifier.create(strictService.clearCache())
                .expectNextCount(1)
                .verifyComplete();

        // Second call is rate limited with no cache -> static fallback via RequestNotPermitted branch
        StepVerifier.create(strictService.callWithFullProtection("success", 0))
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).isEqualTo("static-fallback");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleSlowCall() {
        StepVerifier.create(service.callWithFullProtection("slow", 0))
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).isEqualTo("external-api-slow");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleNullMode() {
        StepVerifier.create(service.callWithFullProtection(null, 0))
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).isEqualTo("external-api-success");
                })
                .verifyComplete();
    }

    @Test
    void shouldClearCache() {
        // Populate cache
        StepVerifier.create(service.callWithFullProtection("success", 0))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(service.clearCache())
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).isEqualTo("cache cleared");
                })
                .verifyComplete();

        StepVerifier.create(service.getCacheSnapshot())
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyCacheSnapshot() {
        StepVerifier.create(service.getCacheSnapshot())
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void shouldAutoCreateCircuitBreakerWhenNotFound() {
        // Fresh factories with no pre-created instances
        CircuitBreakerFactory freshCbFactory = new CircuitBreakerFactory(CircuitBreakerConfig.ofDefaults());
        RateLimiterFactory freshRlFactory = new RateLimiterFactory(RateLimiterConfig.ofDefaults());
        ProductionService freshService = new ProductionService(
                freshCbFactory, freshRlFactory, new ExternalApiService());

        StepVerifier.create(freshService.callWithFullProtection("success", 0))
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).isEqualTo("external-api-success");
                })
                .verifyComplete();

        assertThat(freshCbFactory.get("externalApi")).isPresent();
        assertThat(freshRlFactory.get("externalApi")).isPresent();
    }
}

package com.joker.resilience4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.joker.resilience4j.model.ApiResponse;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class RateLimiterServiceTest {

    @Test
    void shouldReturnSuccessForPermittedCall() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(10)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        RateLimiter rateLimiter = registry.rateLimiter("successTest");
        RateLimiterService service = new RateLimiterService(rateLimiter, registry, new ExternalApiService());

        StepVerifier.create(service.callExternal("success", 0))
                .expectNextMatches(response ->
                        response.success()
                                && response.data() != null
                                && response.data().equals("external-api-success"))
                .verifyComplete();
    }

    @Test
    void shouldRejectWhenPermitsExhausted() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofSeconds(10))
                .timeoutDuration(Duration.ZERO)
                .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        RateLimiter rateLimiter = registry.rateLimiter("exhaustTest");
        RateLimiterService service = new RateLimiterService(rateLimiter, registry, new ExternalApiService());

        // First call consumes the only permit
        StepVerifier.create(service.callExternal("success", 0))
                .expectNextMatches(response -> response.success())
                .verifyComplete();

        // Second call should be rejected
        StepVerifier.create(service.callExternal("success", 0))
                .assertNext(response -> {
                    assertThat(response.success()).isFalse();
                    assertThat(response.error()).isNotNull();
                    assertThat(response.error().componentState()).isEqualTo("RATE_LIMITED");
                })
                .verifyComplete();
    }

    @Test
    void shouldRecoverAfterRefreshPeriod() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofMillis(200))
                .timeoutDuration(Duration.ZERO)
                .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        RateLimiter rateLimiter = registry.rateLimiter("recoverTest");
        RateLimiterService service = new RateLimiterService(rateLimiter, registry, new ExternalApiService());

        // Consume the permit
        StepVerifier.create(service.callExternal("success", 0))
                .expectNextMatches(response -> response.success())
                .verifyComplete();

        // Rejected immediately
        StepVerifier.create(service.callExternal("success", 0))
                .expectNextMatches(response -> !response.success())
                .verifyComplete();

        // Wait for refresh
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Should succeed again
        StepVerifier.create(service.callExternal("success", 0))
                .expectNextMatches(response -> response.success())
                .verifyComplete();
    }

    @Test
    void shouldReturnFallbackWhenRejected() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofSeconds(10))
                .timeoutDuration(Duration.ZERO)
                .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        RateLimiter rateLimiter = registry.rateLimiter("fallbackTest");
        RateLimiterService service = new RateLimiterService(rateLimiter, registry, new ExternalApiService());

        // Consume the permit
        StepVerifier.create(service.callExternal("success", 0))
                .expectNextMatches(response -> response.success())
                .verifyComplete();

        // Fallback should activate
        StepVerifier.create(service.callExternalWithFallback("success", 0))
                .expectNextMatches(response ->
                        response.success()
                                && response.data() != null
                                && response.data().equals("fallback-fallbackTest"))
                .verifyComplete();
    }

    @Test
    void shouldReturnFallbackOnFailure() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(10)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        RateLimiter rateLimiter = registry.rateLimiter("fallbackFailTest");
        RateLimiterService service = new RateLimiterService(rateLimiter, registry, new ExternalApiService());

        StepVerifier.create(service.callExternalWithFallback("failure", 0))
                .expectNextMatches(response ->
                        response.success()
                                && response.data() != null
                                && response.data().equals("fallback-fallbackFailTest"))
                .verifyComplete();
    }

    @Test
    void shouldHandleFailureMode() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(10)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        RateLimiter rateLimiter = registry.rateLimiter("failureTest");
        RateLimiterService service = new RateLimiterService(rateLimiter, registry, new ExternalApiService());

        StepVerifier.create(service.callExternal("failure", 0))
                .assertNext(response -> {
                    assertThat(response.success()).isFalse();
                    assertThat(response.error()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleSlowMode() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(10)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        RateLimiter rateLimiter = registry.rateLimiter("slowTest");
        RateLimiterService service = new RateLimiterService(rateLimiter, registry, new ExternalApiService());

        StepVerifier.create(service.callExternal("slow", 0))
                .expectNextMatches(response ->
                        response.success() && "external-api-slow".equals(response.data()))
                .verifyComplete();
    }

    @Test
    void shouldDefaultToSuccessWhenModeUnknown() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(10)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        RateLimiter rateLimiter = registry.rateLimiter("unknownTest");
        RateLimiterService service = new RateLimiterService(rateLimiter, registry, new ExternalApiService());

        StepVerifier.create(service.callExternal("unknown-mode", 0))
                .expectNextMatches(response ->
                        response.success() && "external-api-success".equals(response.data()))
                .verifyComplete();
    }

    @Test
    void shouldDefaultToSuccessWhenModeIsNull() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(10)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        RateLimiter rateLimiter = registry.rateLimiter("nullModeTest");
        RateLimiterService service = new RateLimiterService(rateLimiter, registry, new ExternalApiService());

        StepVerifier.create(service.callExternal(null, 0))
                .expectNextMatches(response ->
                        response.success() && "external-api-success".equals(response.data()))
                .verifyComplete();
    }

    @Test
    void shouldReturnStatus() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(10)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        RateLimiter rateLimiter = registry.rateLimiter("statusTest");
        RateLimiterService service = new RateLimiterService(rateLimiter, registry, new ExternalApiService());

        StepVerifier.create(service.getStatus())
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).isNotNull();
                    assertThat(response.data().name()).isEqualTo("statusTest");
                    assertThat(response.data().availablePermissions()).isEqualTo(10);
                    assertThat(response.data().numberOfWaitingThreads()).isZero();
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnStatusByName() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(5)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        RateLimiter rateLimiter = registry.rateLimiter("default");
        registry.rateLimiter("other");
        RateLimiterService service = new RateLimiterService(rateLimiter, registry, new ExternalApiService());

        StepVerifier.create(service.getStatus("other"))
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data().name()).isEqualTo("other");
                    assertThat(response.data().availablePermissions()).isEqualTo(5);
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnNotFoundForMissingRateLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(10)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        RateLimiter rateLimiter = registry.rateLimiter("default");
        RateLimiterService service = new RateLimiterService(rateLimiter, registry, new ExternalApiService());

        StepVerifier.create(service.getStatus("nonExistent"))
                .assertNext(response -> {
                    assertThat(response.success()).isFalse();
                    assertThat(response.error()).isNotNull();
                    assertThat(response.error().message()).contains("RateLimiter not found");
                    assertThat(response.error().exception()).isEqualTo("NotFound");
                    assertThat(response.error().componentState()).isEqualTo("UNKNOWN");
                })
                .verifyComplete();
    }

    @Test
    void shouldListRateLimiterNames() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(10)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        RateLimiter rateLimiter = registry.rateLimiter("listTest");
        RateLimiterService service = new RateLimiterService(rateLimiter, registry, new ExternalApiService());

        StepVerifier.create(service.listRateLimiters())
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).contains("listTest");
                })
                .verifyComplete();
    }
}

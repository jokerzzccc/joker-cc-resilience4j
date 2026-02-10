package com.joker.resilience4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.joker.resilience4j.config.RateLimiterFactory;
import com.joker.resilience4j.model.ApiResponse;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
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
        RateLimiterFactory factory = new RateLimiterFactory(config);
        RateLimiter rateLimiter = factory.create("successTest", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiterService service = new RateLimiterService(factory, rateLimiter, new ExternalApiService());

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
        RateLimiterFactory factory = new RateLimiterFactory(config);
        RateLimiter rateLimiter = factory.create("exhaustTest", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiterService service = new RateLimiterService(factory, rateLimiter, new ExternalApiService());

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
        RateLimiterFactory factory = new RateLimiterFactory(config);
        RateLimiter rateLimiter = factory.create("recoverTest", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiterService service = new RateLimiterService(factory, rateLimiter, new ExternalApiService());

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
        RateLimiterFactory factory = new RateLimiterFactory(config);
        RateLimiter rateLimiter = factory.create("fallbackTest", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiterService service = new RateLimiterService(factory, rateLimiter, new ExternalApiService());

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
        RateLimiterFactory factory = new RateLimiterFactory(config);
        RateLimiter rateLimiter = factory.create("fallbackFailTest", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiterService service = new RateLimiterService(factory, rateLimiter, new ExternalApiService());

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
        RateLimiterFactory factory = new RateLimiterFactory(config);
        RateLimiter rateLimiter = factory.create("failureTest", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiterService service = new RateLimiterService(factory, rateLimiter, new ExternalApiService());

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
        RateLimiterFactory factory = new RateLimiterFactory(config);
        RateLimiter rateLimiter = factory.create("slowTest", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiterService service = new RateLimiterService(factory, rateLimiter, new ExternalApiService());

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
        RateLimiterFactory factory = new RateLimiterFactory(config);
        RateLimiter rateLimiter = factory.create("unknownTest", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiterService service = new RateLimiterService(factory, rateLimiter, new ExternalApiService());

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
        RateLimiterFactory factory = new RateLimiterFactory(config);
        RateLimiter rateLimiter = factory.create("nullModeTest", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiterService service = new RateLimiterService(factory, rateLimiter, new ExternalApiService());

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
        RateLimiterFactory factory = new RateLimiterFactory(config);
        RateLimiter rateLimiter = factory.create("statusTest", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiterService service = new RateLimiterService(factory, rateLimiter, new ExternalApiService());

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
        RateLimiterFactory factory = new RateLimiterFactory(config);
        RateLimiter rateLimiter = factory.create("default", RateLimiterFactory.ConfigTemplate.BURST);
        factory.create("other", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiterService service = new RateLimiterService(factory, rateLimiter, new ExternalApiService());

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
        RateLimiterFactory factory = new RateLimiterFactory(config);
        RateLimiter rateLimiter = factory.create("default", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiterService service = new RateLimiterService(factory, rateLimiter, new ExternalApiService());

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
        RateLimiterFactory factory = new RateLimiterFactory(config);
        RateLimiter rateLimiter = factory.create("listTest", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiterService service = new RateLimiterService(factory, rateLimiter, new ExternalApiService());

        StepVerifier.create(service.listRateLimiters())
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).contains("listTest");
                })
                .verifyComplete();
    }

    // --- Stage 5: New tests ---

    @Test
    void shouldCreateRateLimiterByNameWhenMissing() {
        RateLimiterFactory factory = new RateLimiterFactory(RateLimiterConfig.ofDefaults());
        RateLimiter rateLimiter = factory.create("externalApi", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiterService service = new RateLimiterService(factory, rateLimiter, new ExternalApiService());

        StepVerifier.create(service.callExternalWithName("newLimiter", "success", 0))
                .expectNextMatches(response -> response.success() && response.data() != null)
                .verifyComplete();

        assertThat(factory.get("newLimiter")).isPresent();
    }

    @Test
    void shouldReturnCombinedStatus() {
        RateLimiterFactory factory = new RateLimiterFactory(RateLimiterConfig.ofDefaults());
        RateLimiter primary = factory.create("primary", RateLimiterFactory.ConfigTemplate.BURST);
        factory.create("secondary", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiterService service = new RateLimiterService(factory, primary, new ExternalApiService());

        StepVerifier.create(service.getCombinedStatus("primary", "secondary"))
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).isNotNull();
                    assertThat(response.data().primary().name()).isEqualTo("primary");
                    assertThat(response.data().secondary().name()).isEqualTo("secondary");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleComboSuccessCall() {
        RateLimiterFactory factory = new RateLimiterFactory(RateLimiterConfig.ofDefaults());
        factory.create("comboPrimary", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiter defaultRl = factory.create("defaultRl", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiterService service = new RateLimiterService(factory, defaultRl, new ExternalApiService());

        StepVerifier.create(service.callExternalWithCombo("comboPrimary", "comboSecondary", "success", 0))
                .expectNextMatches(response ->
                        response.success() && "external-api-success".equals(response.data()))
                .verifyComplete();
    }

    @Test
    void shouldHandleComboFailureCall() {
        RateLimiterFactory factory = new RateLimiterFactory(RateLimiterConfig.ofDefaults());
        factory.create("comboFailPrimary", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiter defaultRl = factory.create("defaultRl", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiterService service = new RateLimiterService(factory, defaultRl, new ExternalApiService());

        StepVerifier.create(service.callExternalWithCombo("comboFailPrimary", "comboFailSecondary", "failure", 0))
                .assertNext(response -> {
                    assertThat(response.success()).isFalse();
                    assertThat(response.error()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleComboSlowCall() {
        RateLimiterFactory factory = new RateLimiterFactory(RateLimiterConfig.ofDefaults());
        factory.create("comboSlowPrimary", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiter defaultRl = factory.create("defaultRl", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiterService service = new RateLimiterService(factory, defaultRl, new ExternalApiService());

        StepVerifier.create(service.callExternalWithCombo("comboSlowPrimary", "comboSlowSecondary", "slow", 0))
                .expectNextMatches(response ->
                        response.success() && "external-api-slow".equals(response.data()))
                .verifyComplete();
    }

    @Test
    void shouldFallbackToDefaultNameWhenNameIsNull() {
        RateLimiterFactory factory = new RateLimiterFactory(RateLimiterConfig.ofDefaults());
        RateLimiter rateLimiter = factory.create("defaultRl", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiterService service = new RateLimiterService(factory, rateLimiter, new ExternalApiService());

        StepVerifier.create(service.callExternalWithName(null, "success", 0))
                .expectNextMatches(response ->
                        response.success() && "external-api-success".equals(response.data()))
                .verifyComplete();
    }

    @Test
    void shouldFallbackToDefaultNameWhenNameIsBlank() {
        RateLimiterFactory factory = new RateLimiterFactory(RateLimiterConfig.ofDefaults());
        RateLimiter rateLimiter = factory.create("defaultRl", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiterService service = new RateLimiterService(factory, rateLimiter, new ExternalApiService());

        StepVerifier.create(service.callExternalWithName("  ", "success", 0))
                .expectNextMatches(response ->
                        response.success() && "external-api-success".equals(response.data()))
                .verifyComplete();
    }

    @Test
    void shouldRejectComboWhenPrimaryExhausted() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofSeconds(10))
                .timeoutDuration(Duration.ZERO)
                .build();
        RateLimiterFactory factory = new RateLimiterFactory(config);
        RateLimiter primary = factory.create("comboExhaustPrimary", RateLimiterFactory.ConfigTemplate.BURST);
        RateLimiterService service = new RateLimiterService(factory, primary, new ExternalApiService());

        // Consume the primary permit
        StepVerifier.create(service.callExternal("success", 0))
                .expectNextMatches(response -> response.success())
                .verifyComplete();

        // Combo should be rejected because primary is exhausted
        StepVerifier.create(service.callExternalWithCombo(
                "comboExhaustPrimary", "comboExhaustSecondary", "success", 0))
                .assertNext(response -> {
                    assertThat(response.success()).isFalse();
                    assertThat(response.error()).isNotNull();
                    assertThat(response.error().componentState()).isEqualTo("RATE_LIMITED");
                })
                .verifyComplete();
    }
}

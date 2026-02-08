package com.joker.resilience4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.joker.resilience4j.config.CircuitBreakerFactory;
import com.joker.resilience4j.model.ApiResponse;
import com.joker.resilience4j.model.CombinedCircuitBreakerStatus;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CircuitBreakerServiceTest {

    @Test
    void shouldReturnSuccessForHealthyCall() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(4)
                        .minimumNumberOfCalls(2)
                        .waitDurationInOpenState(Duration.ofSeconds(1))
                        .build()
        );
        CircuitBreaker circuitBreaker = factory.create("successTest", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreakerService service = new CircuitBreakerService(factory, circuitBreaker, new ExternalApiService());

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
        CircuitBreakerFactory factory = new CircuitBreakerFactory(
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(2)
                        .minimumNumberOfCalls(2)
                        .waitDurationInOpenState(Duration.ofSeconds(1))
                        .build()
        );
        CircuitBreaker circuitBreaker = factory.create("failureTest", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreakerService service = new CircuitBreakerService(factory, circuitBreaker, new ExternalApiService());

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
        CircuitBreakerFactory factory = new CircuitBreakerFactory(
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(2)
                        .minimumNumberOfCalls(2)
                        .permittedNumberOfCallsInHalfOpenState(1)
                        .waitDurationInOpenState(Duration.ofMillis(150))
                        .build()
        );
        CircuitBreaker circuitBreaker = factory.create("recoveryTest", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreakerService service = new CircuitBreakerService(factory, circuitBreaker, new ExternalApiService());

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
        CircuitBreakerFactory factory = new CircuitBreakerFactory(
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .failureRateThreshold(100)
                        .slowCallRateThreshold(100)
                        .slidingWindowSize(4)
                        .minimumNumberOfCalls(2)
                        .build()
        );
        CircuitBreaker circuitBreaker = factory.create("slowTest", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreakerService service = new CircuitBreakerService(factory, circuitBreaker, new ExternalApiService());

        StepVerifier.create(service.callExternal("slow", 0))
                .expectNextMatches(response -> response.success() && response.data() != null)
                .verifyComplete();
    }

    @Test
    void shouldDefaultToSuccessWhenModeUnknown() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(2)
                        .minimumNumberOfCalls(2)
                        .build()
        );
        CircuitBreaker circuitBreaker = factory.create("defaultTest", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreakerService service = new CircuitBreakerService(factory, circuitBreaker, new ExternalApiService());

        StepVerifier.create(service.callExternal("unknown-mode", 0))
                .expectNextMatches(response -> response.success() && response.data() != null)
                .verifyComplete();
    }

    @Test
    void shouldReturnStatus() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.ofDefaults()
        );
        CircuitBreaker circuitBreaker = factory.create("statusTest", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreakerService service = new CircuitBreakerService(factory, circuitBreaker, new ExternalApiService());

        StepVerifier.create(service.getStatus())
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnFallbackWhenEnabled() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.ofDefaults()
        );
        CircuitBreaker circuitBreaker = factory.create("fallbackTest", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreakerService service = new CircuitBreakerService(factory, circuitBreaker, new ExternalApiService());

        StepVerifier.create(service.callExternalWithFallback("failure", 0))
                .expectNextMatches(response ->
                        response.success()
                                && response.data() != null
                                && response.data().equals("fallback-fallbackTest"))
                .verifyComplete();
    }

    @Test
    void shouldCreateCircuitBreakerByNameWhenMissing() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.ofDefaults()
        );
        CircuitBreaker circuitBreaker = factory.create("externalApi", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreakerService service = new CircuitBreakerService(factory, circuitBreaker, new ExternalApiService());

        StepVerifier.create(service.callExternalWithName("newBreaker", "success", 0))
                .expectNextMatches(response -> response.success() && response.data() != null)
                .verifyComplete();

        assertThat(factory.get("newBreaker")).isPresent();
    }

    @Test
    void shouldReturnCombinedStatus() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.ofDefaults()
        );
        CircuitBreaker primary = factory.create("primary", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreaker secondary = factory.create("secondary", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreakerService service = new CircuitBreakerService(factory, primary, new ExternalApiService());

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
    void shouldListCircuitBreakerNames() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.ofDefaults()
        );
        CircuitBreaker circuitBreaker = factory.create("externalApi", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreakerService service = new CircuitBreakerService(factory, circuitBreaker, new ExternalApiService());

        StepVerifier.create(service.listCircuitBreakers())
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).contains("externalApi");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnNotFoundForMissingCircuitBreaker() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.ofDefaults()
        );
        CircuitBreaker circuitBreaker = factory.create("externalApi", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreakerService service = new CircuitBreakerService(factory, circuitBreaker, new ExternalApiService());

        StepVerifier.create(service.getStatus("nonExistent"))
                .assertNext(response -> {
                    assertThat(response.success()).isFalse();
                    assertThat(response.error()).isNotNull();
                    assertThat(response.error().message()).contains("CircuitBreaker not found");
                    assertThat(response.error().exception()).isEqualTo("NotFound");
                    assertThat(response.error().circuitBreakerState()).isEqualTo("UNKNOWN");
                })
                .verifyComplete();
    }

    @Test
    void shouldDefaultToSuccessWhenModeIsNull() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.ofDefaults()
        );
        CircuitBreaker circuitBreaker = factory.create("nullModeTest", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreakerService service = new CircuitBreakerService(factory, circuitBreaker, new ExternalApiService());

        StepVerifier.create(service.callExternal(null, 0))
                .expectNextMatches(response ->
                        response.success() && "external-api-success".equals(response.data()))
                .verifyComplete();
    }

    @Test
    void shouldFallbackToDefaultNameWhenNameIsNull() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.ofDefaults()
        );
        CircuitBreaker circuitBreaker = factory.create("defaultCb", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreakerService service = new CircuitBreakerService(factory, circuitBreaker, new ExternalApiService());

        StepVerifier.create(service.callExternalWithName(null, "success", 0))
                .expectNextMatches(response ->
                        response.success() && "external-api-success".equals(response.data()))
                .verifyComplete();
    }

    @Test
    void shouldFallbackToDefaultNameWhenNameIsBlank() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.ofDefaults()
        );
        CircuitBreaker circuitBreaker = factory.create("defaultCb", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreakerService service = new CircuitBreakerService(factory, circuitBreaker, new ExternalApiService());

        StepVerifier.create(service.callExternalWithName("  ", "success", 0))
                .expectNextMatches(response ->
                        response.success() && "external-api-success".equals(response.data()))
                .verifyComplete();
    }

    @Test
    void shouldHandleComboSuccessCall() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.ofDefaults()
        );
        factory.create("comboPrimary", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreaker defaultCb = factory.create("defaultCb", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreakerService service = new CircuitBreakerService(factory, defaultCb, new ExternalApiService());

        StepVerifier.create(service.callExternalWithCombo("comboPrimary", "comboSecondary", "success", 0))
                .expectNextMatches(response ->
                        response.success() && "external-api-success".equals(response.data()))
                .verifyComplete();
    }

    @Test
    void shouldHandleComboFailureCall() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.ofDefaults()
        );
        factory.create("comboFailPrimary", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreaker defaultCb = factory.create("defaultCb", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreakerService service = new CircuitBreakerService(factory, defaultCb, new ExternalApiService());

        StepVerifier.create(service.callExternalWithCombo("comboFailPrimary", "comboFailSecondary", "failure", 0))
                .assertNext(response -> {
                    assertThat(response.success()).isFalse();
                    assertThat(response.error()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleComboSlowCall() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.ofDefaults()
        );
        factory.create("comboSlowPrimary", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreaker defaultCb = factory.create("defaultCb", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreakerService service = new CircuitBreakerService(factory, defaultCb, new ExternalApiService());

        StepVerifier.create(service.callExternalWithCombo("comboSlowPrimary", "comboSlowSecondary", "slow", 0))
                .expectNextMatches(response ->
                        response.success() && "external-api-slow".equals(response.data()))
                .verifyComplete();
    }

    @Test
    void shouldReturnCallNotPermittedWhenCircuitIsOpen() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(2)
                        .minimumNumberOfCalls(2)
                        .waitDurationInOpenState(Duration.ofSeconds(60))
                        .build()
        );
        CircuitBreaker circuitBreaker = factory.create("openTest", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreakerService service = new CircuitBreakerService(factory, circuitBreaker, new ExternalApiService());

        StepVerifier.create(service.callExternal("failure", 0)).expectNextCount(1).verifyComplete();
        StepVerifier.create(service.callExternal("failure", 0)).expectNextCount(1).verifyComplete();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        StepVerifier.create(service.callExternal("success", 0))
                .assertNext(response -> {
                    assertThat(response.success()).isFalse();
                    assertThat(response.error()).isNotNull();
                    assertThat(response.error().circuitBreakerState()).isEqualTo("OPEN");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnFallbackWhenCircuitIsOpen() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(2)
                        .minimumNumberOfCalls(2)
                        .waitDurationInOpenState(Duration.ofSeconds(60))
                        .build()
        );
        CircuitBreaker circuitBreaker = factory.create("fallbackOpenTest", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreakerService service = new CircuitBreakerService(factory, circuitBreaker, new ExternalApiService());

        StepVerifier.create(service.callExternal("failure", 0)).expectNextCount(1).verifyComplete();
        StepVerifier.create(service.callExternal("failure", 0)).expectNextCount(1).verifyComplete();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        StepVerifier.create(service.callExternalWithFallback("success", 0))
                .expectNextMatches(response ->
                        response.success() && "fallback-fallbackOpenTest".equals(response.data()))
                .verifyComplete();
    }

    @Test
    void shouldReturnPrimaryStateWhenPrimaryIsOpenInCombo() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(2)
                        .minimumNumberOfCalls(2)
                        .waitDurationInOpenState(Duration.ofSeconds(60))
                        .build()
        );
        CircuitBreaker primary = factory.create("comboOpenPrimary", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreakerService service = new CircuitBreakerService(factory, primary, new ExternalApiService());

        StepVerifier.create(service.callExternal("failure", 0)).expectNextCount(1).verifyComplete();
        StepVerifier.create(service.callExternal("failure", 0)).expectNextCount(1).verifyComplete();
        assertThat(primary.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        StepVerifier.create(service.callExternalWithCombo("comboOpenPrimary", "comboOpenSecondary", "success", 0))
                .assertNext(response -> {
                    assertThat(response.success()).isFalse();
                    assertThat(response.error()).isNotNull();
                    assertThat(response.error().circuitBreakerState()).isEqualTo("OPEN");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnSecondaryStateWhenSecondaryIsOpenInCombo() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.ofDefaults()
        );
        factory.create("secPrimary", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreaker secondary = factory.create("secSecondary", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        secondary.transitionToOpenState();
        CircuitBreaker defaultCb = factory.create("defaultCb", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        CircuitBreakerService service = new CircuitBreakerService(factory, defaultCb, new ExternalApiService());

        StepVerifier.create(service.callExternalWithCombo("secPrimary", "secSecondary", "success", 0))
                .assertNext(response -> {
                    assertThat(response.success()).isFalse();
                    assertThat(response.error()).isNotNull();
                    assertThat(response.error().circuitBreakerState()).isEqualTo("OPEN");
                })
                .verifyComplete();
    }
}

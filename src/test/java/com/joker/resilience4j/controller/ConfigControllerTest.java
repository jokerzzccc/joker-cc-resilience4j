package com.joker.resilience4j.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.joker.resilience4j.config.CircuitBreakerFactory;
import com.joker.resilience4j.config.RateLimiterFactory;
import com.joker.resilience4j.model.ApiResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ConfigControllerTest {

    private CircuitBreakerFactory cbFactory;
    private RateLimiterFactory rlFactory;
    private ConfigController controller;

    @BeforeEach
    void setUp() {
        cbFactory = new CircuitBreakerFactory(CircuitBreakerConfig.ofDefaults());
        rlFactory = new RateLimiterFactory(RateLimiterConfig.ofDefaults());
        cbFactory.create("testCb", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        rlFactory.create("testRl", RateLimiterFactory.ConfigTemplate.BURST);
        controller = new ConfigController(cbFactory, rlFactory);
    }

    @Test
    void shouldUpdateCircuitBreakerConfig() {
        StepVerifier.create(controller.updateCircuitBreaker("testCb", 30, 40, 20, 10, 10000))
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).contains("testCb");
                    assertThat(response.data()).contains("updated");
                })
                .verifyComplete();

        assertThat(cbFactory.get("testCb")).isPresent();
        assertThat(cbFactory.get("testCb").get().getCircuitBreakerConfig()
                .getFailureRateThreshold()).isEqualTo(30.0f);
    }

    @Test
    void shouldGetCircuitBreakerConfig() {
        StepVerifier.create(controller.getCircuitBreakerConfig("testCb"))
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).containsKey("name");
                    assertThat(response.data().get("name")).isEqualTo("testCb");
                    assertThat(response.data()).containsKey("state");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnNotFoundForMissingCircuitBreaker() {
        StepVerifier.create(controller.getCircuitBreakerConfig("nonExistent"))
                .assertNext(response -> {
                    assertThat(response.success()).isFalse();
                    assertThat(response.error()).isNotNull();
                    assertThat(response.error().message()).contains("not found");
                })
                .verifyComplete();
    }

    @Test
    void shouldUpdateRateLimiterConfig() {
        StepVerifier.create(controller.updateRateLimiter("testRl", 50, 2000, 100))
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).contains("testRl");
                    assertThat(response.data()).contains("updated");
                })
                .verifyComplete();

        assertThat(rlFactory.get("testRl")).isPresent();
        assertThat(rlFactory.get("testRl").get().getRateLimiterConfig()
                .getLimitForPeriod()).isEqualTo(50);
    }

    @Test
    void shouldGetRateLimiterConfig() {
        StepVerifier.create(controller.getRateLimiterConfig("testRl"))
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).containsKey("name");
                    assertThat(response.data().get("name")).isEqualTo("testRl");
                    assertThat(response.data()).containsKey("limitForPeriod");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnNotFoundForMissingRateLimiter() {
        StepVerifier.create(controller.getRateLimiterConfig("nonExistent"))
                .assertNext(response -> {
                    assertThat(response.success()).isFalse();
                    assertThat(response.error()).isNotNull();
                    assertThat(response.error().message()).contains("not found");
                })
                .verifyComplete();
    }
}

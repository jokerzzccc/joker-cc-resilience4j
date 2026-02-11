package com.joker.resilience4j.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.joker.resilience4j.config.CircuitBreakerFactory;
import com.joker.resilience4j.config.RateLimiterFactory;
import com.joker.resilience4j.service.ExternalApiService;
import com.joker.resilience4j.service.ProductionService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ProductionControllerTest {

    private ProductionController controller;

    @BeforeEach
    void setUp() {
        CircuitBreakerFactory cbFactory = new CircuitBreakerFactory(CircuitBreakerConfig.ofDefaults());
        RateLimiterFactory rlFactory = new RateLimiterFactory(RateLimiterConfig.ofDefaults());
        cbFactory.create("externalApi", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        rlFactory.create("externalApi", RateLimiterFactory.ConfigTemplate.BURST);
        ProductionService service = new ProductionService(cbFactory, rlFactory, new ExternalApiService());
        controller = new ProductionController(service);
    }

    @Test
    void shouldCallWithFullProtection() {
        StepVerifier.create(controller.test("success", 0))
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).isEqualTo("external-api-success");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnCacheSnapshot() {
        // First populate cache
        StepVerifier.create(controller.test("success", 0))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(controller.cache())
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).containsKey("lastSuccess");
                })
                .verifyComplete();
    }

    @Test
    void shouldClearCache() {
        // Populate and clear
        StepVerifier.create(controller.test("success", 0))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(controller.clearCache())
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).isEqualTo("cache cleared");
                })
                .verifyComplete();
    }
}

package com.joker.resilience4j.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class MetricsControllerTest {

    @Test
    void shouldReturnMetricsSnapshot() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Counter cbCounter = Counter.builder("resilience4j.custom.circuitbreaker.calls.total")
                .tag("component", "circuitbreaker")
                .register(meterRegistry);
        Counter rlCounter = Counter.builder("resilience4j.custom.ratelimiter.calls.total")
                .tag("component", "ratelimiter")
                .register(meterRegistry);
        Timer timer = Timer.builder("resilience4j.custom.api.call.duration")
                .tag("component", "api")
                .register(meterRegistry);

        cbCounter.increment(3.0);
        rlCounter.increment(2.0);

        MetricsController controller = new MetricsController(cbCounter, rlCounter, timer);

        StepVerifier.create(controller.snapshot())
                .expectNextMatches(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data().circuitBreakerTotalCalls()).isEqualTo(3.0);
                    assertThat(response.data().rateLimiterTotalCalls()).isEqualTo(2.0);
                    assertThat(response.data().apiCallCount()).isEqualTo(0);
                    return true;
                })
                .verifyComplete();
    }
}

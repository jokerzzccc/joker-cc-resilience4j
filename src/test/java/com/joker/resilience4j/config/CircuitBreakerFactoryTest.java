package com.joker.resilience4j.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CircuitBreakerFactoryTest {

    @Test
    void shouldCreateFromTemplates() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(CircuitBreakerConfig.ofDefaults());

        assertThat(factory.create("fast", CircuitBreakerFactory.ConfigTemplate.FAST_FAIL).getName())
                .isEqualTo("fast");
        assertThat(factory.create("slow", CircuitBreakerFactory.ConfigTemplate.SLOW_CALL).getName())
                .isEqualTo("slow");
        assertThat(factory.create("hybrid", CircuitBreakerFactory.ConfigTemplate.HYBRID).getName())
                .isEqualTo("hybrid");
    }

    @Test
    void shouldCacheAndReturnSameInstance() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(CircuitBreakerConfig.ofDefaults());

        var first = factory.create("cache", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        var second = factory.create("cache", CircuitBreakerFactory.ConfigTemplate.HYBRID);

        assertThat(first).isSameAs(second);
    }

    @Test
    void shouldUpdateInstance() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(CircuitBreakerConfig.ofDefaults());
        var original = factory.create("update", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        var updated = factory.update("update", CircuitBreakerConfig.custom()
                .failureRateThreshold(10.0f)
                .slidingWindowSize(2)
                .minimumNumberOfCalls(1)
                .build());

        assertThat(updated).isNotSameAs(original);
        assertThat(factory.get("update")).contains(updated);
    }

    @Test
    void shouldListNames() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(CircuitBreakerConfig.ofDefaults());
        factory.create("a", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        factory.create("b", CircuitBreakerFactory.ConfigTemplate.HYBRID);

        assertThat(factory.listNames()).contains("a", "b");
    }

    @Test
    void shouldCreateFromCustomizer() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(CircuitBreakerConfig.ofDefaults());

        var circuitBreaker = factory.create("custom", builder -> builder
                .failureRateThreshold(60.0f)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
        );

        assertThat(circuitBreaker.getName()).isEqualTo("custom");
        assertThat(factory.get("custom")).isPresent();
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(CircuitBreakerConfig.ofDefaults());

        assertThat(factory.get("missing")).isEmpty();
    }

    @Test
    void shouldFireSlowCallRateExceededEvent() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(CircuitBreakerConfig.ofDefaults());
        var cb = factory.create("slowEvent", builder -> builder
                .slowCallRateThreshold(50.0f)
                .slowCallDurationThreshold(Duration.ofMillis(5))
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
        );

        Runnable slowCall = CircuitBreaker.decorateRunnable(cb, () -> {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        slowCall.run();
        slowCall.run();

        assertThat(cb.getMetrics().getSlowCallRate()).isGreaterThan(0.0f);
    }

    @Test
    void shouldFireCallNotPermittedEvent() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(CircuitBreakerConfig.ofDefaults());
        var cb = factory.create("notPermitted", CircuitBreakerFactory.ConfigTemplate.HYBRID);
        cb.transitionToOpenState();

        try {
            CircuitBreaker.decorateRunnable(cb, () -> {}).run();
        } catch (CallNotPermittedException ignored) {
            // expected
        }

        assertThat(cb.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);
    }

    @Test
    void shouldReattachListenersAfterUpdate() {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(CircuitBreakerConfig.ofDefaults());
        factory.create("reattach", CircuitBreakerFactory.ConfigTemplate.HYBRID);

        var updated = factory.update("reattach", CircuitBreakerConfig.custom()
                .failureRateThreshold(10.0f)
                .slidingWindowSize(2)
                .minimumNumberOfCalls(1)
                .build());

        updated.transitionToOpenState();

        try {
            CircuitBreaker.decorateRunnable(updated, () -> {}).run();
        } catch (CallNotPermittedException ignored) {
            // expected - verifies listeners were re-attached
        }

        assertThat(updated.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);
    }
}

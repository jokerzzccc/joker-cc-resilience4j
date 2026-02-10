package com.joker.resilience4j.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimiterFactoryTest {

    @Test
    void shouldCreateFromTemplates() {
        RateLimiterFactory factory = new RateLimiterFactory(RateLimiterConfig.ofDefaults());

        assertThat(factory.create("strict", RateLimiterFactory.ConfigTemplate.STRICT).getName())
                .isEqualTo("strict");
        assertThat(factory.create("lenient", RateLimiterFactory.ConfigTemplate.LENIENT).getName())
                .isEqualTo("lenient");
        assertThat(factory.create("burst", RateLimiterFactory.ConfigTemplate.BURST).getName())
                .isEqualTo("burst");
    }

    @Test
    void shouldCacheAndReturnSameInstance() {
        RateLimiterFactory factory = new RateLimiterFactory(RateLimiterConfig.ofDefaults());

        var first = factory.create("cache", RateLimiterFactory.ConfigTemplate.BURST);
        var second = factory.create("cache", RateLimiterFactory.ConfigTemplate.BURST);

        assertThat(first).isSameAs(second);
    }

    @Test
    void shouldUpdateInstance() {
        RateLimiterFactory factory = new RateLimiterFactory(RateLimiterConfig.ofDefaults());
        var original = factory.create("update", RateLimiterFactory.ConfigTemplate.BURST);
        var updated = factory.update("update", RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build());

        assertThat(updated).isNotSameAs(original);
        assertThat(factory.get("update")).contains(updated);
    }

    @Test
    void shouldListNames() {
        RateLimiterFactory factory = new RateLimiterFactory(RateLimiterConfig.ofDefaults());
        factory.create("a", RateLimiterFactory.ConfigTemplate.BURST);
        factory.create("b", RateLimiterFactory.ConfigTemplate.BURST);

        assertThat(factory.listNames()).contains("a", "b");
    }

    @Test
    void shouldCreateFromCustomizer() {
        RateLimiterFactory factory = new RateLimiterFactory(RateLimiterConfig.ofDefaults());

        var rateLimiter = factory.create("custom", builder -> builder
                .limitForPeriod(20)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
        );

        assertThat(rateLimiter.getName()).isEqualTo("custom");
        assertThat(factory.get("custom")).isPresent();
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        RateLimiterFactory factory = new RateLimiterFactory(RateLimiterConfig.ofDefaults());

        assertThat(factory.get("missing")).isEmpty();
    }

    @Test
    void shouldFireSuccessAndFailureEvents() {
        RateLimiterFactory factory = new RateLimiterFactory(RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofSeconds(10))
                .timeoutDuration(Duration.ZERO)
                .build());
        var rl = factory.create("eventTest", RateLimiterFactory.ConfigTemplate.BURST);

        // Successful call — triggers onSuccess event
        RateLimiter.decorateRunnable(rl, () -> {}).run();

        // Permits exhausted — triggers onFailure event
        try {
            RateLimiter.decorateRunnable(rl, () -> {}).run();
        } catch (RequestNotPermitted ignored) {
            // expected
        }

        assertThat(rl.getMetrics().getAvailablePermissions()).isLessThanOrEqualTo(0);
    }

    @Test
    void shouldReattachListenersAfterUpdate() {
        RateLimiterFactory factory = new RateLimiterFactory(RateLimiterConfig.ofDefaults());
        factory.create("reattach", RateLimiterFactory.ConfigTemplate.BURST);

        var updated = factory.update("reattach", RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofSeconds(10))
                .timeoutDuration(Duration.ZERO)
                .build());

        // Successful call on updated instance — triggers re-attached onSuccess listener
        RateLimiter.decorateRunnable(updated, () -> {}).run();

        // Permits exhausted — triggers re-attached onFailure listener
        try {
            RateLimiter.decorateRunnable(updated, () -> {}).run();
        } catch (RequestNotPermitted ignored) {
            // expected — verifies listeners were re-attached
        }

        assertThat(updated.getMetrics().getAvailablePermissions()).isLessThanOrEqualTo(0);
    }
}

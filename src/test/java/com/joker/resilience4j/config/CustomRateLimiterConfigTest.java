package com.joker.resilience4j.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CustomRateLimiterConfigTest {

    @Test
    void shouldCreateRateLimiterConfig() {
        CustomRateLimiterConfig config = new CustomRateLimiterConfig();
        RateLimiterConfig rlConfig = config.rateLimiterConfig();

        assertThat(rlConfig.getLimitForPeriod()).isEqualTo(10);
        assertThat(rlConfig.getLimitRefreshPeriod()).isEqualTo(Duration.ofSeconds(1));
        assertThat(rlConfig.getTimeoutDuration()).isEqualTo(Duration.ZERO);
    }

    @Test
    void shouldCreateRegistryAndRateLimiter() {
        CustomRateLimiterConfig config = new CustomRateLimiterConfig();
        RateLimiterConfig rlConfig = config.rateLimiterConfig();
        RateLimiterRegistry registry = config.rateLimiterRegistry(rlConfig);
        RateLimiter rateLimiter = config.rateLimiter(registry);

        assertThat(rateLimiter.getName()).isEqualTo("externalApi");
        assertThat(registry).isNotNull();
    }
}

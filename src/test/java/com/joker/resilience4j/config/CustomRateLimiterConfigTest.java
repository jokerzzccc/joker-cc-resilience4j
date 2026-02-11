package com.joker.resilience4j.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CustomRateLimiterConfigTest {

    private static final ResilienceConfigProperties DEFAULT_PROPS = new ResilienceConfigProperties(
            new ResilienceConfigProperties.CircuitBreakerProps(50, 50, 2000, 10, 5, 2, 5000),
            new ResilienceConfigProperties.RateLimiterProps(10, 1000, 0)
    );

    @Test
    void shouldCreateRateLimiterConfig() {
        CustomRateLimiterConfig config = new CustomRateLimiterConfig();
        RateLimiterConfig rlConfig = config.rateLimiterConfig(DEFAULT_PROPS);

        assertThat(rlConfig.getLimitForPeriod()).isEqualTo(10);
        assertThat(rlConfig.getLimitRefreshPeriod()).isEqualTo(Duration.ofSeconds(1));
        assertThat(rlConfig.getTimeoutDuration()).isEqualTo(Duration.ZERO);
    }

    @Test
    void shouldCreateRegistryAndRateLimiter() {
        CustomRateLimiterConfig config = new CustomRateLimiterConfig();
        RateLimiterConfig rlConfig = config.rateLimiterConfig(DEFAULT_PROPS);
        RateLimiterRegistry registry = config.rateLimiterRegistry(rlConfig);

        assertThat(registry).isNotNull();
    }

    @Test
    void shouldCreateRateLimiterFactory() {
        CustomRateLimiterConfig config = new CustomRateLimiterConfig();
        RateLimiterConfig rlConfig = config.rateLimiterConfig(DEFAULT_PROPS);
        RateLimiterFactory factory = config.rateLimiterFactory(rlConfig);

        assertThat(factory).isNotNull();
    }

    @Test
    void shouldCreateRateLimiterFromFactory() {
        CustomRateLimiterConfig config = new CustomRateLimiterConfig();
        RateLimiterConfig rlConfig = config.rateLimiterConfig(DEFAULT_PROPS);
        RateLimiterFactory factory = config.rateLimiterFactory(rlConfig);
        RateLimiter rateLimiter = config.rateLimiter(factory);

        assertThat(rateLimiter.getName()).isEqualTo("externalApi");
    }
}

package com.joker.resilience4j.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResilienceConfigPropertiesTest {

    @Test
    void shouldCreateWithExplicitValues() {
        ResilienceConfigProperties.CircuitBreakerProps cbProps =
                new ResilienceConfigProperties.CircuitBreakerProps(30, 40, 3000, 100, 20, 5, 30000);
        ResilienceConfigProperties.RateLimiterProps rlProps =
                new ResilienceConfigProperties.RateLimiterProps(50, 2000, 100);
        ResilienceConfigProperties props = new ResilienceConfigProperties(cbProps, rlProps);

        assertThat(props.circuitBreaker().failureRateThreshold()).isEqualTo(30);
        assertThat(props.circuitBreaker().slowCallRateThreshold()).isEqualTo(40);
        assertThat(props.circuitBreaker().slowCallDurationThresholdMs()).isEqualTo(3000);
        assertThat(props.circuitBreaker().slidingWindowSize()).isEqualTo(100);
        assertThat(props.circuitBreaker().minimumNumberOfCalls()).isEqualTo(20);
        assertThat(props.circuitBreaker().permittedNumberOfCallsInHalfOpenState()).isEqualTo(5);
        assertThat(props.circuitBreaker().waitDurationInOpenStateMs()).isEqualTo(30000);

        assertThat(props.rateLimiter().limitForPeriod()).isEqualTo(50);
        assertThat(props.rateLimiter().limitRefreshPeriodMs()).isEqualTo(2000);
        assertThat(props.rateLimiter().timeoutDurationMs()).isEqualTo(100);
    }
}

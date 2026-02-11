package com.joker.resilience4j.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CustomCircuitBreakerConfigTest {

    private static final ResilienceConfigProperties DEFAULT_PROPS = new ResilienceConfigProperties(
            new ResilienceConfigProperties.CircuitBreakerProps(50, 50, 2000, 10, 5, 2, 5000),
            new ResilienceConfigProperties.RateLimiterProps(10, 1000, 0)
    );

    @Test
    void shouldCreateCircuitBreakerConfig() {
        CustomCircuitBreakerConfig config = new CustomCircuitBreakerConfig();
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig cbConfig =
                config.circuitBreakerConfig(DEFAULT_PROPS);

        assertThat(cbConfig.getFailureRateThreshold()).isEqualTo(50.0f);
        assertThat(cbConfig.getSlowCallRateThreshold()).isEqualTo(50.0f);
        assertThat(cbConfig.getSlowCallDurationThreshold()).isEqualTo(Duration.ofSeconds(2));
        assertThat(cbConfig.getSlidingWindowSize()).isEqualTo(10);
        assertThat(cbConfig.getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(cbConfig.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(2);
        assertThat(cbConfig.getWaitIntervalFunctionInOpenState().apply(1))
                .isEqualTo(Duration.ofSeconds(5).toMillis());
    }

    @Test
    void shouldCreateRegistryAndCircuitBreaker() {
        CustomCircuitBreakerConfig config = new CustomCircuitBreakerConfig();
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig cbConfig =
                config.circuitBreakerConfig(DEFAULT_PROPS);
        CircuitBreakerRegistry registry = config.circuitBreakerRegistry(cbConfig);
        CircuitBreakerFactory factory = config.circuitBreakerFactory(cbConfig);
        CircuitBreaker circuitBreaker = config.circuitBreaker(factory);

        assertThat(circuitBreaker.getName()).isEqualTo("externalApi");
        assertThat(registry).isNotNull();
        assertThat(factory.get("externalApi")).isPresent();
    }
}

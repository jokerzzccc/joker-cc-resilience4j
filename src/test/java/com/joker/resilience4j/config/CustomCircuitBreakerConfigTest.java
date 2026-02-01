package com.joker.resilience4j.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CustomCircuitBreakerConfigTest {

    @Test
    void shouldCreateCircuitBreakerConfig() {
        CustomCircuitBreakerConfig config = new CustomCircuitBreakerConfig();
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig cbConfig = config.circuitBreakerConfig();

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
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig cbConfig = config.circuitBreakerConfig();
        CircuitBreakerRegistry registry = config.circuitBreakerRegistry(cbConfig);
        CircuitBreaker circuitBreaker = config.circuitBreaker(registry);

        assertThat(circuitBreaker.getName()).isEqualTo("externalApi");
        assertThat(registry.getAllCircuitBreakers()).isNotEmpty();
    }
}

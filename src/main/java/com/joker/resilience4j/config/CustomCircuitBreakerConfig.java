package com.joker.resilience4j.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CustomCircuitBreakerConfig {

    @Bean
    public CircuitBreakerConfig circuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .slowCallRateThreshold(50.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(2)
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .recordExceptions(Exception.class)
                .build();
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(
            CircuitBreakerConfig circuitBreakerConfig) {
        return CircuitBreakerRegistry.of(circuitBreakerConfig);
    }

    @Bean
    public CircuitBreakerFactory circuitBreakerFactory(CircuitBreakerConfig circuitBreakerConfig) {
        return new CircuitBreakerFactory(circuitBreakerConfig);
    }

    @Bean
    public CircuitBreaker circuitBreaker(CircuitBreakerFactory circuitBreakerFactory) {
        return circuitBreakerFactory.create("externalApi", CircuitBreakerFactory.ConfigTemplate.HYBRID);
    }
}

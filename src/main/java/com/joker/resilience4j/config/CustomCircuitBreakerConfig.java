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
    public CircuitBreakerConfig circuitBreakerConfig(ResilienceConfigProperties properties) {
        ResilienceConfigProperties.CircuitBreakerProps cb = properties.circuitBreaker();
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(cb.failureRateThreshold())
                .slowCallRateThreshold(cb.slowCallRateThreshold())
                .slowCallDurationThreshold(Duration.ofMillis(cb.slowCallDurationThresholdMs()))
                .slidingWindowSize(cb.slidingWindowSize())
                .minimumNumberOfCalls(cb.minimumNumberOfCalls())
                .permittedNumberOfCallsInHalfOpenState(cb.permittedNumberOfCallsInHalfOpenState())
                .waitDurationInOpenState(Duration.ofMillis(cb.waitDurationInOpenStateMs()))
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

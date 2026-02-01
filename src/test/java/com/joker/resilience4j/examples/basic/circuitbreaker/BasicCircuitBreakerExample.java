package com.joker.resilience4j.examples.basic.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;

public class BasicCircuitBreakerExample {

    public static void main(String[] args) {
        // Basic configuration: small window, quick open for demo
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
                .waitDurationInOpenState(Duration.ofSeconds(2))
                .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of("example", config);

        for (int i = 0; i < 4; i++) {
            int index = i;
            try {
                // Simulate alternating success/failure
                String result = circuitBreaker.executeSupplier(() -> {
                    if (index % 2 == 0) {
                        throw new IllegalStateException("boom");
                    }
                    return "ok";
                });
                System.out.println("call=" + index + " result=" + result);
            } catch (Exception ex) {
                System.out.println("call=" + index + " error=" + ex.getMessage());
            }
        }

        System.out.println("state=" + circuitBreaker.getState());
    }
}

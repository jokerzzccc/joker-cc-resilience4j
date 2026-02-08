package com.joker.resilience4j.model;

public record CombinedCircuitBreakerStatus(
        CircuitBreakerStatus primary,
        CircuitBreakerStatus secondary
) {
}

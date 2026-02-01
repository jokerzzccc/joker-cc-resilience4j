package com.joker.resilience4j.model;

public record CircuitBreakerStatus(
        String name,
        String state,
        float failureRate,
        float slowCallRate,
        long bufferedCalls,
        long failedCalls,
        long slowCalls,
        long notPermittedCalls
) {
}

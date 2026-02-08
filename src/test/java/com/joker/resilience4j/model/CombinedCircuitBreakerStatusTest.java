package com.joker.resilience4j.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CombinedCircuitBreakerStatusTest {

    @Test
    void shouldHoldBothStatuses() {
        CircuitBreakerStatus primary = new CircuitBreakerStatus(
                "primary",
                "CLOSED",
                0.0f,
                0.0f,
                0,
                0,
                0,
                0
        );
        CircuitBreakerStatus secondary = new CircuitBreakerStatus(
                "secondary",
                "OPEN",
                100.0f,
                0.0f,
                0,
                1,
                0,
                2
        );

        CombinedCircuitBreakerStatus combined = new CombinedCircuitBreakerStatus(primary, secondary);

        assertThat(combined.primary()).isEqualTo(primary);
        assertThat(combined.secondary()).isEqualTo(secondary);
    }
}

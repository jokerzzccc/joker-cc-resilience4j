package com.joker.resilience4j.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CombinedRateLimiterStatusTest {

    @Test
    void shouldHoldBothStatuses() {
        RateLimiterStatus primary = new RateLimiterStatus("primary", 10, 0);
        RateLimiterStatus secondary = new RateLimiterStatus("secondary", 5, 2);

        CombinedRateLimiterStatus combined = new CombinedRateLimiterStatus(primary, secondary);

        assertThat(combined.primary()).isEqualTo(primary);
        assertThat(combined.secondary()).isEqualTo(secondary);
    }
}

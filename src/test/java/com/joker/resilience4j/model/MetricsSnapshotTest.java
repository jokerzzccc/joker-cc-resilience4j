package com.joker.resilience4j.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MetricsSnapshotTest {

    @Test
    void shouldCreateMetricsSnapshot() {
        MetricsSnapshot snapshot = new MetricsSnapshot(
                10.0, 5.0, 15, 1500.0, 100.0, 250.0);

        assertThat(snapshot.circuitBreakerTotalCalls()).isEqualTo(10.0);
        assertThat(snapshot.rateLimiterTotalCalls()).isEqualTo(5.0);
        assertThat(snapshot.apiCallCount()).isEqualTo(15);
        assertThat(snapshot.apiCallTotalTimeMs()).isEqualTo(1500.0);
        assertThat(snapshot.apiCallMeanTimeMs()).isEqualTo(100.0);
        assertThat(snapshot.apiCallMaxTimeMs()).isEqualTo(250.0);
    }
}

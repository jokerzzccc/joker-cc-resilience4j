package com.joker.resilience4j.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PushgatewayStatusTest {

    @Test
    void shouldCreatePushgatewayStatus() {
        PushgatewayStatus status = new PushgatewayStatus(
                "test-job", "test-instance", "configured", "2026-02-21T00:00:00Z", "success"
        );

        assertThat(status.jobName()).isEqualTo("test-job");
        assertThat(status.instanceName()).isEqualTo("test-instance");
        assertThat(status.pushgatewayState()).isEqualTo("configured");
        assertThat(status.lastPushTime()).isEqualTo("2026-02-21T00:00:00Z");
        assertThat(status.lastPushResult()).isEqualTo("success");
    }
}

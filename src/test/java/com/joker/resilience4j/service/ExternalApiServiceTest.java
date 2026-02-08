package com.joker.resilience4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ExternalApiServiceTest {

    private final ExternalApiService service = new ExternalApiService();

    @Test
    void shouldReturnSuccess() {
        assertThat(service.callSuccess()).isEqualTo("external-api-success");
    }

    @Test
    void shouldThrowOnFailure() {
        assertThatThrownBy(service::callFailure)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("External API failure");
    }

    @Test
    void shouldReturnSlowResponseWhenDelayIsZero() {
        assertThat(service.callSlow(Duration.ZERO)).isEqualTo("external-api-slow");
    }

    @Test
    void shouldReturnSlowResponseWhenDelayIsNull() {
        assertThat(service.callSlow(null)).isEqualTo("external-api-slow");
    }

    @Test
    void shouldReturnSlowResponseWithPositiveDelay() {
        assertThat(service.callSlow(Duration.ofMillis(1))).isEqualTo("external-api-slow");
    }

    @Test
    void shouldReturnSlowResponseWhenDelayIsNegative() {
        assertThat(service.callSlow(Duration.ofMillis(-1))).isEqualTo("external-api-slow");
    }

    @Test
    void shouldHandleInterruptedSleep() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> service.callSlow(Duration.ofMillis(50)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("External API interrupted");
        } finally {
            Thread.interrupted();
        }
    }
}

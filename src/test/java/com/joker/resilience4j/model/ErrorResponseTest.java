package com.joker.resilience4j.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorResponseTest {

    @Test
    void shouldCreateFromExceptionWithMessage() {
        RuntimeException ex = new RuntimeException("boom");
        ErrorResponse response = ErrorResponse.from(ex, "CLOSED");

        assertThat(response.message()).isEqualTo("boom");
        assertThat(response.exception()).isEqualTo("RuntimeException");
        assertThat(response.circuitBreakerState()).isEqualTo("CLOSED");
    }

    @Test
    void shouldCreateFromExceptionWithNullMessage() {
        RuntimeException ex = new RuntimeException((String) null);
        ErrorResponse response = ErrorResponse.from(ex, "OPEN");

        assertThat(response.message()).isEqualTo("Unexpected error");
        assertThat(response.exception()).isEqualTo("RuntimeException");
        assertThat(response.circuitBreakerState()).isEqualTo("OPEN");
    }
}

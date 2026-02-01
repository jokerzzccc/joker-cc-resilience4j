package com.joker.resilience4j.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void shouldBuildSuccessResponse() {
        ApiResponse<String> response = ApiResponse.success("ok");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("ok");
        assertThat(response.error()).isNull();
    }

    @Test
    void shouldBuildFailureResponse() {
        ErrorResponse error = new ErrorResponse("boom", "RuntimeException", "OPEN");
        ApiResponse<String> response = ApiResponse.failure(error);

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.error()).isEqualTo(error);
    }
}

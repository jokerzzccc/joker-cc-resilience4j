package com.joker.resilience4j.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.joker.resilience4j.model.ApiResponse;
import com.joker.resilience4j.model.CircuitBreakerStatus;
import com.joker.resilience4j.service.CircuitBreakerService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CircuitBreakerControllerTest {

    @Test
    void shouldReturnTestResponse() {
        CircuitBreakerService service = mock(CircuitBreakerService.class);
        ApiResponse<String> expected = ApiResponse.success("ok");
        when(service.callExternal("success", 0)).thenReturn(Mono.just(expected));

        CircuitBreakerController controller = new CircuitBreakerController(service);

        StepVerifier.create(controller.test("success", 0))
                .expectNext(expected)
                .verifyComplete();
    }

    @Test
    void shouldReturnStateResponse() {
        CircuitBreakerService service = mock(CircuitBreakerService.class);
        CircuitBreakerStatus status = new CircuitBreakerStatus(
                "cb",
                "CLOSED",
                0.0f,
                0.0f,
                0,
                0,
                0,
                0
        );
        ApiResponse<CircuitBreakerStatus> expected = ApiResponse.success(status);
        when(service.getStatus()).thenReturn(expected);

        CircuitBreakerController controller = new CircuitBreakerController(service);

        StepVerifier.create(controller.state())
                .expectNext(expected)
                .verifyComplete();
    }
}

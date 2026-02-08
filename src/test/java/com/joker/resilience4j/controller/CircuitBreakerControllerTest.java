package com.joker.resilience4j.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.joker.resilience4j.model.ApiResponse;
import com.joker.resilience4j.model.CombinedCircuitBreakerStatus;
import com.joker.resilience4j.model.CircuitBreakerStatus;
import java.util.Set;
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
        when(service.getStatus("externalApi")).thenReturn(Mono.just(expected));

        CircuitBreakerController controller = new CircuitBreakerController(service);

        StepVerifier.create(controller.state("externalApi"))
                .expectNext(expected)
                .verifyComplete();
    }

    @Test
    void shouldReturnFallbackResponse() {
        CircuitBreakerService service = mock(CircuitBreakerService.class);
        ApiResponse<String> expected = ApiResponse.success("fallback-externalApi");
        when(service.callExternalWithFallback("failure", 0)).thenReturn(Mono.just(expected));

        CircuitBreakerController controller = new CircuitBreakerController(service);

        StepVerifier.create(controller.testFallback("failure", 0))
                .expectNext(expected)
                .verifyComplete();
    }

    @Test
    void shouldReturnNamedResponse() {
        CircuitBreakerService service = mock(CircuitBreakerService.class);
        ApiResponse<String> expected = ApiResponse.success("ok");
        when(service.callExternalWithName("custom", "success", 0)).thenReturn(Mono.just(expected));

        CircuitBreakerController controller = new CircuitBreakerController(service);

        StepVerifier.create(controller.testReactor("custom", "success", 0))
                .expectNext(expected)
                .verifyComplete();
    }

    @Test
    void shouldReturnComboResponse() {
        CircuitBreakerService service = mock(CircuitBreakerService.class);
        ApiResponse<String> expected = ApiResponse.success("ok");
        when(service.callExternalWithCombo("primary", "secondary", "success", 0)).thenReturn(Mono.just(expected));

        CircuitBreakerController controller = new CircuitBreakerController(service);

        StepVerifier.create(controller.testCombo("primary", "secondary", "success", 0))
                .expectNext(expected)
                .verifyComplete();
    }

    @Test
    void shouldReturnComboState() {
        CircuitBreakerService service = mock(CircuitBreakerService.class);
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
        ApiResponse<CombinedCircuitBreakerStatus> expected = ApiResponse.success(combined);
        when(service.getCombinedStatus("primary", "secondary")).thenReturn(Mono.just(expected));

        CircuitBreakerController controller = new CircuitBreakerController(service);

        StepVerifier.create(controller.stateCombo("primary", "secondary"))
                .expectNext(expected)
                .verifyComplete();
    }

    @Test
    void shouldReturnNames() {
        CircuitBreakerService service = mock(CircuitBreakerService.class);
        ApiResponse<Set<String>> expected = ApiResponse.success(Set.of("externalApi", "secondaryApi"));
        when(service.listCircuitBreakers()).thenReturn(Mono.just(expected));

        CircuitBreakerController controller = new CircuitBreakerController(service);

        StepVerifier.create(controller.states())
                .expectNext(expected)
                .verifyComplete();
    }
}

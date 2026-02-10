package com.joker.resilience4j.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.joker.resilience4j.model.ApiResponse;
import com.joker.resilience4j.model.CombinedRateLimiterStatus;
import com.joker.resilience4j.model.RateLimiterStatus;
import com.joker.resilience4j.service.RateLimiterService;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RateLimiterControllerTest {

    @Test
    void shouldReturnTestResponse() {
        RateLimiterService service = mock(RateLimiterService.class);
        ApiResponse<String> expected = ApiResponse.success("ok");
        when(service.callExternal("success", 0)).thenReturn(Mono.just(expected));

        RateLimiterController controller = new RateLimiterController(service);

        StepVerifier.create(controller.test("success", 0))
                .expectNext(expected)
                .verifyComplete();
    }

    @Test
    void shouldReturnFallbackResponse() {
        RateLimiterService service = mock(RateLimiterService.class);
        ApiResponse<String> expected = ApiResponse.success("fallback-externalApi");
        when(service.callExternalWithFallback("failure", 0)).thenReturn(Mono.just(expected));

        RateLimiterController controller = new RateLimiterController(service);

        StepVerifier.create(controller.testFallback("failure", 0))
                .expectNext(expected)
                .verifyComplete();
    }

    @Test
    void shouldReturnStateResponse() {
        RateLimiterService service = mock(RateLimiterService.class);
        RateLimiterStatus status = new RateLimiterStatus("externalApi", 10, 0);
        ApiResponse<RateLimiterStatus> expected = ApiResponse.success(status);
        when(service.getStatus("externalApi")).thenReturn(Mono.just(expected));

        RateLimiterController controller = new RateLimiterController(service);

        StepVerifier.create(controller.state("externalApi"))
                .expectNext(expected)
                .verifyComplete();
    }

    @Test
    void shouldReturnNames() {
        RateLimiterService service = mock(RateLimiterService.class);
        ApiResponse<Set<String>> expected = ApiResponse.success(Set.of("externalApi"));
        when(service.listRateLimiters()).thenReturn(Mono.just(expected));

        RateLimiterController controller = new RateLimiterController(service);

        StepVerifier.create(controller.states())
                .expectNext(expected)
                .verifyComplete();
    }

    @Test
    void shouldReturnNamedResponse() {
        RateLimiterService service = mock(RateLimiterService.class);
        ApiResponse<String> expected = ApiResponse.success("external-api-success");
        when(service.callExternalWithName("myLimiter", "success", 0)).thenReturn(Mono.just(expected));

        RateLimiterController controller = new RateLimiterController(service);

        StepVerifier.create(controller.testReactor("myLimiter", "success", 0))
                .expectNext(expected)
                .verifyComplete();
    }

    @Test
    void shouldReturnComboResponse() {
        RateLimiterService service = mock(RateLimiterService.class);
        ApiResponse<String> expected = ApiResponse.success("external-api-success");
        when(service.callExternalWithCombo("primary", "secondary", "success", 0))
                .thenReturn(Mono.just(expected));

        RateLimiterController controller = new RateLimiterController(service);

        StepVerifier.create(controller.testCombo("primary", "secondary", "success", 0))
                .expectNext(expected)
                .verifyComplete();
    }

    @Test
    void shouldReturnComboState() {
        RateLimiterService service = mock(RateLimiterService.class);
        RateLimiterStatus primary = new RateLimiterStatus("primary", 10, 0);
        RateLimiterStatus secondary = new RateLimiterStatus("secondary", 5, 0);
        CombinedRateLimiterStatus combined = new CombinedRateLimiterStatus(primary, secondary);
        ApiResponse<CombinedRateLimiterStatus> expected = ApiResponse.success(combined);
        when(service.getCombinedStatus("primary", "secondary")).thenReturn(Mono.just(expected));

        RateLimiterController controller = new RateLimiterController(service);

        StepVerifier.create(controller.stateCombo("primary", "secondary"))
                .expectNext(expected)
                .verifyComplete();
    }
}

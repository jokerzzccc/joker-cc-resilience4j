package com.joker.resilience4j.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.joker.resilience4j.service.PushgatewayService;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PushgatewayControllerTest {

    private PushgatewayController controller;

    @BeforeEach
    void setUp() {
        PrometheusMeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        WebClient webClient = WebClient.builder()
                .baseUrl("http://pushgateway:9091")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body("")
                        .build()))
                .build();
        PushgatewayService service = new PushgatewayService(meterRegistry, webClient, "test-job", "test-instance");
        controller = new PushgatewayController(service);
    }

    @Test
    void shouldPush() {
        StepVerifier.create(controller.push())
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).contains("Pushed");
                })
                .verifyComplete();
    }

    @Test
    void shouldDelete() {
        StepVerifier.create(controller.delete())
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).contains("Deleted");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnStatus() {
        StepVerifier.create(controller.status())
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data().jobName()).isEqualTo("test-job");
                    assertThat(response.data().instanceName()).isEqualTo("test-instance");
                })
                .verifyComplete();
    }
}

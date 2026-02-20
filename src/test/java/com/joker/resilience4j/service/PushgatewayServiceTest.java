package com.joker.resilience4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PushgatewayServiceTest {

    private PrometheusMeterRegistry meterRegistry;
    private ClientRequest capturedRequest;

    @BeforeEach
    void setUp() {
        meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        capturedRequest = null;
    }

    private PushgatewayService createServiceWithMockWebClient(HttpStatus responseStatus) {
        ExchangeFunction exchangeFunction = request -> {
            capturedRequest = request;
            return Mono.just(ClientResponse.create(responseStatus)
                    .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                    .body("")
                    .build());
        };
        WebClient webClient = WebClient.builder()
                .baseUrl("http://pushgateway:9091")
                .exchangeFunction(exchangeFunction)
                .build();
        return new PushgatewayService(meterRegistry, webClient, "test-job", "test-instance");
    }

    private PushgatewayService createServiceWithErrorWebClient() {
        ExchangeFunction exchangeFunction = request -> {
            capturedRequest = request;
            return Mono.error(new RuntimeException("Connection refused"));
        };
        WebClient webClient = WebClient.builder()
                .baseUrl("http://pushgateway:9091")
                .exchangeFunction(exchangeFunction)
                .build();
        return new PushgatewayService(meterRegistry, webClient, "test-job", "test-instance");
    }

    @Test
    void shouldPushMetricsSuccessfully() {
        PushgatewayService service = createServiceWithMockWebClient(HttpStatus.OK);

        StepVerifier.create(service.push())
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).contains("Pushed");
                    assertThat(response.data()).contains("lines to Pushgateway");
                })
                .verifyComplete();

        assertThat(capturedRequest).isNotNull();
        assertThat(capturedRequest.method()).isEqualTo(HttpMethod.POST);
        assertThat(capturedRequest.url().getPath()).isEqualTo("/metrics/job/test-job/instance/test-instance");
    }

    @Test
    void shouldHandlePushError() {
        PushgatewayService service = createServiceWithErrorWebClient();

        StepVerifier.create(service.push())
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).contains("Push failed");
                    assertThat(response.data()).contains("Connection refused");
                })
                .verifyComplete();
    }

    @Test
    void shouldDeleteMetricsSuccessfully() {
        PushgatewayService service = createServiceWithMockWebClient(HttpStatus.ACCEPTED);

        StepVerifier.create(service.delete())
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).contains("Deleted metrics group");
                    assertThat(response.data()).contains("job=test-job");
                    assertThat(response.data()).contains("instance=test-instance");
                })
                .verifyComplete();

        assertThat(capturedRequest).isNotNull();
        assertThat(capturedRequest.method()).isEqualTo(HttpMethod.DELETE);
        assertThat(capturedRequest.url().getPath()).isEqualTo("/metrics/job/test-job/instance/test-instance");
    }

    @Test
    void shouldHandleDeleteError() {
        PushgatewayService service = createServiceWithErrorWebClient();

        StepVerifier.create(service.delete())
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data()).contains("Delete failed");
                    assertThat(response.data()).contains("Connection refused");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnStatusWithNeverPushed() {
        PushgatewayService service = createServiceWithMockWebClient(HttpStatus.OK);

        StepVerifier.create(service.status())
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.data().jobName()).isEqualTo("test-job");
                    assertThat(response.data().instanceName()).isEqualTo("test-instance");
                    assertThat(response.data().lastPushTime()).isEqualTo("never");
                    assertThat(response.data().lastPushResult()).isEqualTo("never");
                })
                .verifyComplete();
    }

    @Test
    void shouldUpdateStatusAfterSuccessfulPush() {
        PushgatewayService service = createServiceWithMockWebClient(HttpStatus.OK);

        // First push
        StepVerifier.create(service.push()).expectNextCount(1).verifyComplete();

        // Check status updated
        StepVerifier.create(service.status())
                .assertNext(response -> {
                    assertThat(response.data().lastPushTime()).isNotEqualTo("never");
                    assertThat(response.data().lastPushResult()).isEqualTo("success");
                })
                .verifyComplete();
    }

    @Test
    void shouldUpdateStatusAfterFailedPush() {
        PushgatewayService service = createServiceWithErrorWebClient();

        // Push fails
        StepVerifier.create(service.push()).expectNextCount(1).verifyComplete();

        // Check status reflects failure
        StepVerifier.create(service.status())
                .assertNext(response -> {
                    assertThat(response.data().lastPushTime()).isNotEqualTo("never");
                    assertThat(response.data().lastPushResult()).startsWith("failed:");
                })
                .verifyComplete();
    }

    @Test
    void shouldScrapeMetricsFromRegistry() {
        // Register a custom counter to verify scrape includes it
        meterRegistry.counter("test.push.counter", "type", "test").increment();

        PushgatewayService service = createServiceWithMockWebClient(HttpStatus.OK);

        String scrapeOutput = meterRegistry.scrape();
        assertThat(scrapeOutput).contains("test_push_counter_total");
    }
}

package com.joker.resilience4j.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.joker.resilience4j.config.PushgatewayConfig.PushgatewayProperties;
import com.joker.resilience4j.model.ApiResponse;
import com.joker.resilience4j.service.PushgatewayService;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class PushgatewayConfigTest {

    @Test
    void shouldCreatePushgatewayPropertiesWithDefaults() {
        PushgatewayProperties props = new PushgatewayProperties(
                false, "http://localhost:9091", "resilience4j-learning", 15
        );

        assertThat(props.enabled()).isFalse();
        assertThat(props.url()).isEqualTo("http://localhost:9091");
        assertThat(props.jobName()).isEqualTo("resilience4j-learning");
        assertThat(props.pushIntervalSeconds()).isEqualTo(15);
    }

    @Test
    void shouldCreatePushgatewayPropertiesWithCustomValues() {
        PushgatewayProperties props = new PushgatewayProperties(
                true, "http://pushgateway:9092", "my-job", 30
        );

        assertThat(props.enabled()).isTrue();
        assertThat(props.url()).isEqualTo("http://pushgateway:9092");
        assertThat(props.jobName()).isEqualTo("my-job");
        assertThat(props.pushIntervalSeconds()).isEqualTo(30);
    }

    @Test
    void shouldResolveInstanceName() {
        String instanceName = PushgatewayConfig.resolveInstanceName(
                () -> InetAddress.getLocalHost().getHostName()
        );
        assertThat(instanceName).isNotNull();
        assertThat(instanceName).isNotEmpty();
    }

    @Test
    void shouldReturnUnknownWhenHostnameResolutionFails() {
        String instanceName = PushgatewayConfig.resolveInstanceName(() -> {
            throw new Exception("simulated failure");
        });
        assertThat(instanceName).isEqualTo("unknown");
    }

    @Test
    void shouldShutdownGracefullyWithNoScheduler() {
        PushgatewayConfig config = new PushgatewayConfig();
        config.shutdown();
    }

    @Test
    void shouldCreatePushgatewayServiceBean() {
        PushgatewayConfig config = new PushgatewayConfig();
        PrometheusMeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        PushgatewayProperties props = new PushgatewayProperties(
                true, "http://localhost:9091", "test-job", 3600
        );

        PushgatewayService service = config.pushgatewayService(meterRegistry, props);

        assertThat(service).isNotNull();
        config.shutdown();
    }

    @Test
    void shouldShutdownActiveScheduler() {
        PushgatewayConfig config = new PushgatewayConfig();
        PrometheusMeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        PushgatewayProperties props = new PushgatewayProperties(
                true, "http://localhost:9091", "test-job", 3600
        );

        config.pushgatewayService(meterRegistry, props);
        config.shutdown();
        config.shutdown();
    }

    @Test
    void shouldHandleOnPushResult() {
        PushgatewayConfig config = new PushgatewayConfig();
        config.onPushResult(ApiResponse.success("test-data"));
    }

    @Test
    void shouldHandleOnPushError() {
        PushgatewayConfig config = new PushgatewayConfig();
        config.onPushError(new RuntimeException("test error"));
    }
}

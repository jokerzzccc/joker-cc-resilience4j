package com.joker.resilience4j.config;

import com.joker.resilience4j.model.ApiResponse;
import com.joker.resilience4j.service.PushgatewayService;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.time.Duration;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * Pushgateway 配置，通过 {@code app.pushgateway.enabled=true} 启用。
 *
 * <p>启用后自动创建 {@link PushgatewayService} Bean，并启动定时推送 Scheduler。
 * 使用 Reactor {@link Flux#interval} 实现定时推送，保持响应式风格。</p>
 */
@Configuration
@ConditionalOnProperty(name = "app.pushgateway.enabled", havingValue = "true")
public class PushgatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(PushgatewayConfig.class);

    private Disposable schedulerDisposable;

    @ConfigurationProperties(prefix = "app.pushgateway")
    public record PushgatewayProperties(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("http://localhost:9091") String url,
            @DefaultValue("resilience4j-learning") String jobName,
            @DefaultValue("15") int pushIntervalSeconds
    ) {
    }

    @Bean
    public PushgatewayService pushgatewayService(
            PrometheusMeterRegistry meterRegistry,
            PushgatewayProperties properties
    ) {
        WebClient webClient = WebClient.builder()
                .baseUrl(properties.url())
                .build();

        String instanceName = resolveInstanceName(() -> InetAddress.getLocalHost().getHostName());

        PushgatewayService service = new PushgatewayService(
                meterRegistry, webClient, properties.jobName(), instanceName
        );

        startScheduledPush(service, properties.pushIntervalSeconds());

        log.info("Pushgateway enabled: url={}, job={}, instance={}, interval={}s",
                properties.url(), properties.jobName(), instanceName, properties.pushIntervalSeconds());

        return service;
    }

    void startScheduledPush(PushgatewayService service, int intervalSeconds) {
        schedulerDisposable = Flux.interval(Duration.ofSeconds(intervalSeconds))
                .flatMap(tick -> service.push())
                .subscribe(
                        this::onPushResult,
                        this::onPushError
                );
    }

    void onPushResult(ApiResponse<String> result) {
        log.debug("Scheduled push result: {}", result.data());
    }

    void onPushError(Throwable error) {
        log.error("Scheduled push error: {}", error.getMessage());
    }

    static String resolveInstanceName(Callable<String> hostnameProvider) {
        try {
            return hostnameProvider.call();
        } catch (Exception e) {
            return "unknown";
        }
    }

    @PreDestroy
    public void shutdown() {
        if (schedulerDisposable != null && !schedulerDisposable.isDisposed()) {
            schedulerDisposable.dispose();
            log.info("Pushgateway scheduled push stopped");
        }
    }
}

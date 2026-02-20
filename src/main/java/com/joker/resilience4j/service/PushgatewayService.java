package com.joker.resilience4j.service;

import com.joker.resilience4j.model.ApiResponse;
import com.joker.resilience4j.model.PushgatewayStatus;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Pushgateway 推送服务，通过 {@link PrometheusMeterRegistry#scrape()} 获取指标文本，
 * 再通过 {@link WebClient} POST 到 Pushgateway REST API。
 *
 * <p>Pushgateway REST API：
 * <ul>
 *   <li>{@code POST /metrics/job/{job}/instance/{instance}} — 推送指标</li>
 *   <li>{@code DELETE /metrics/job/{job}/instance/{instance}} — 删除指标</li>
 * </ul>
 */
public class PushgatewayService {

    private static final Logger log = LoggerFactory.getLogger(PushgatewayService.class);

    private final PrometheusMeterRegistry meterRegistry;
    private final WebClient webClient;
    private final String jobName;
    private final String instanceName;
    private final AtomicReference<Instant> lastPushTime = new AtomicReference<>();
    private final AtomicReference<String> lastPushResult = new AtomicReference<>("never");

    public PushgatewayService(
            PrometheusMeterRegistry meterRegistry,
            WebClient webClient,
            String jobName,
            String instanceName
    ) {
        this.meterRegistry = meterRegistry;
        this.webClient = webClient;
        this.jobName = jobName;
        this.instanceName = instanceName;
    }

    /**
     * 推送当前所有指标到 Pushgateway。
     *
     * <p>调用 {@code PrometheusMeterRegistry.scrape()} 获取 Prometheus 文本格式的指标，
     * 然后 POST 到 {@code /metrics/job/{jobName}/instance/{instanceName}}。</p>
     */
    public Mono<ApiResponse<String>> push() {
        String metricsText = meterRegistry.scrape();
        String uri = buildUri();

        return webClient.post()
                .uri(uri)
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(metricsText)
                .retrieve()
                .toBodilessEntity()
                .map(response -> {
                    lastPushTime.set(Instant.now());
                    lastPushResult.set("success");
                    log.info("Pushed metrics to Pushgateway: job={}, instance={}", jobName, instanceName);
                    return ApiResponse.success("Pushed " + metricsText.lines().count() + " lines to Pushgateway");
                })
                .onErrorResume(ex -> {
                    lastPushTime.set(Instant.now());
                    lastPushResult.set("failed: " + ex.getMessage());
                    log.error("Failed to push metrics to Pushgateway: {}", ex.getMessage());
                    return Mono.just(ApiResponse.success("Push failed: " + ex.getMessage()));
                });
    }

    /**
     * 删除 Pushgateway 上该 job/instance 分组的所有指标。
     */
    public Mono<ApiResponse<String>> delete() {
        String uri = buildUri();

        return webClient.delete()
                .uri(uri)
                .retrieve()
                .toBodilessEntity()
                .map(response -> {
                    log.info("Deleted metrics from Pushgateway: job={}, instance={}", jobName, instanceName);
                    return ApiResponse.success("Deleted metrics group: job=" + jobName + ", instance=" + instanceName);
                })
                .onErrorResume(ex -> {
                    log.error("Failed to delete metrics from Pushgateway: {}", ex.getMessage());
                    return Mono.just(ApiResponse.success("Delete failed: " + ex.getMessage()));
                });
    }

    /**
     * 获取当前 Pushgateway 推送状态。
     */
    public Mono<ApiResponse<PushgatewayStatus>> status() {
        Instant lastTime = lastPushTime.get();
        return Mono.just(ApiResponse.success(new PushgatewayStatus(
                jobName,
                instanceName,
                "configured",
                lastTime != null ? lastTime.toString() : "never",
                lastPushResult.get()
        )));
    }

    private String buildUri() {
        return "/metrics/job/" + jobName + "/instance/" + instanceName;
    }
}

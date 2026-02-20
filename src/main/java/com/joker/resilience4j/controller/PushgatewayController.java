package com.joker.resilience4j.controller;

import com.joker.resilience4j.model.ApiResponse;
import com.joker.resilience4j.model.PushgatewayStatus;
import com.joker.resilience4j.service.PushgatewayService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Pushgateway 手动操作端点，仅当 {@code app.pushgateway.enabled=true} 时注册。
 *
 * <ul>
 *   <li>{@code POST /api/pushgateway/push} — 手动推送一次</li>
 *   <li>{@code DELETE /api/pushgateway/delete} — 删除 Pushgateway 上的指标</li>
 *   <li>{@code GET /api/pushgateway/status} — 查看推送状态</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/pushgateway")
@ConditionalOnProperty(name = "app.pushgateway.enabled", havingValue = "true")
public class PushgatewayController {

    private final PushgatewayService pushgatewayService;

    public PushgatewayController(PushgatewayService pushgatewayService) {
        this.pushgatewayService = pushgatewayService;
    }

    @PostMapping("/push")
    public Mono<ApiResponse<String>> push() {
        return pushgatewayService.push();
    }

    @DeleteMapping("/delete")
    public Mono<ApiResponse<String>> delete() {
        return pushgatewayService.delete();
    }

    @GetMapping("/status")
    public Mono<ApiResponse<PushgatewayStatus>> status() {
        return pushgatewayService.status();
    }
}

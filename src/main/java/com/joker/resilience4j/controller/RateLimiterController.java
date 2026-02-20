package com.joker.resilience4j.controller;

import com.joker.resilience4j.model.ApiResponse;
import com.joker.resilience4j.model.CombinedRateLimiterStatus;
import com.joker.resilience4j.model.RateLimiterStatus;
import com.joker.resilience4j.service.RateLimiterService;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 限流器测试端点，提供单实例/组合调用、fallback 降级和状态查询接口。
 */
@RestController
@RequestMapping("/api/rate-limiter")
public class RateLimiterController {

    private final RateLimiterService rateLimiterService;

    public RateLimiterController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @GetMapping("/test")
    public Mono<ApiResponse<String>> test(
            @RequestParam(defaultValue = "success") String mode,
            @RequestParam(defaultValue = "0") long delayMs
    ) {
        return rateLimiterService.callExternal(mode, delayMs);
    }

    @GetMapping("/test-fallback")
    public Mono<ApiResponse<String>> testFallback(
            @RequestParam(defaultValue = "success") String mode,
            @RequestParam(defaultValue = "0") long delayMs
    ) {
        return rateLimiterService.callExternalWithFallback(mode, delayMs);
    }

    @GetMapping("/test-reactor")
    public Mono<ApiResponse<String>> testReactor(
            @RequestParam(defaultValue = "externalApi") String name,
            @RequestParam(defaultValue = "success") String mode,
            @RequestParam(defaultValue = "0") long delayMs
    ) {
        return rateLimiterService.callExternalWithName(name, mode, delayMs);
    }

    @GetMapping("/test-combo")
    public Mono<ApiResponse<String>> testCombo(
            @RequestParam(defaultValue = "externalApi") String primary,
            @RequestParam(defaultValue = "secondaryApi") String secondary,
            @RequestParam(defaultValue = "success") String mode,
            @RequestParam(defaultValue = "0") long delayMs
    ) {
        return rateLimiterService.callExternalWithCombo(primary, secondary, mode, delayMs);
    }

    @GetMapping("/state")
    public Mono<ApiResponse<RateLimiterStatus>> state(
            @RequestParam(defaultValue = "externalApi") String name
    ) {
        return rateLimiterService.getStatus(name);
    }

    @GetMapping("/state-combo")
    public Mono<ApiResponse<CombinedRateLimiterStatus>> stateCombo(
            @RequestParam(defaultValue = "externalApi") String primary,
            @RequestParam(defaultValue = "secondaryApi") String secondary
    ) {
        return rateLimiterService.getCombinedStatus(primary, secondary);
    }

    @GetMapping("/states")
    public Mono<ApiResponse<Set<String>>> states() {
        return rateLimiterService.listRateLimiters();
    }
}

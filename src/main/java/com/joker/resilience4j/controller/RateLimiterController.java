package com.joker.resilience4j.controller;

import com.joker.resilience4j.model.ApiResponse;
import com.joker.resilience4j.model.RateLimiterStatus;
import com.joker.resilience4j.service.RateLimiterService;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

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

    @GetMapping("/state")
    public Mono<ApiResponse<RateLimiterStatus>> state(
            @RequestParam(defaultValue = "externalApi") String name
    ) {
        return rateLimiterService.getStatus(name);
    }

    @GetMapping("/states")
    public Mono<ApiResponse<Set<String>>> states() {
        return rateLimiterService.listRateLimiters();
    }
}

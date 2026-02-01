package com.joker.resilience4j.controller;

import com.joker.resilience4j.model.ApiResponse;
import com.joker.resilience4j.model.CircuitBreakerStatus;
import com.joker.resilience4j.service.CircuitBreakerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/circuit-breaker")
public class CircuitBreakerController {

    private final CircuitBreakerService circuitBreakerService;

    public CircuitBreakerController(CircuitBreakerService circuitBreakerService) {
        this.circuitBreakerService = circuitBreakerService;
    }

    @GetMapping("/test")
    public Mono<ApiResponse<String>> test(
            @RequestParam(defaultValue = "success") String mode,
            @RequestParam(defaultValue = "0") long delayMs
    ) {
        return circuitBreakerService.callExternal(mode, delayMs);
    }

    @GetMapping("/state")
    public Mono<ApiResponse<CircuitBreakerStatus>> state() {
        return Mono.just(circuitBreakerService.getStatus());
    }
}

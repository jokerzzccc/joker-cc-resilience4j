package com.joker.resilience4j.controller;

import com.joker.resilience4j.model.ApiResponse;
import com.joker.resilience4j.model.CombinedCircuitBreakerStatus;
import com.joker.resilience4j.model.CircuitBreakerStatus;
import com.joker.resilience4j.service.CircuitBreakerService;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 熔断器测试端点，提供单实例/组合调用、fallback 降级和状态查询接口。
 */
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

    @GetMapping("/test-fallback")
    public Mono<ApiResponse<String>> testFallback(
            @RequestParam(defaultValue = "success") String mode,
            @RequestParam(defaultValue = "0") long delayMs
    ) {
        return circuitBreakerService.callExternalWithFallback(mode, delayMs);
    }

    @GetMapping("/test-reactor")
    public Mono<ApiResponse<String>> testReactor(
            @RequestParam(defaultValue = "externalApi") String name,
            @RequestParam(defaultValue = "success") String mode,
            @RequestParam(defaultValue = "0") long delayMs
    ) {
        return circuitBreakerService.callExternalWithName(name, mode, delayMs);
    }

    @GetMapping("/test-combo")
    public Mono<ApiResponse<String>> testCombo(
            @RequestParam(defaultValue = "externalApi") String primary,
            @RequestParam(defaultValue = "secondaryApi") String secondary,
            @RequestParam(defaultValue = "success") String mode,
            @RequestParam(defaultValue = "0") long delayMs
    ) {
        return circuitBreakerService.callExternalWithCombo(primary, secondary, mode, delayMs);
    }

    @GetMapping("/state")
    public Mono<ApiResponse<CircuitBreakerStatus>> state(
            @RequestParam(defaultValue = "externalApi") String name
    ) {
        return circuitBreakerService.getStatus(name);
    }

    @GetMapping("/state-combo")
    public Mono<ApiResponse<CombinedCircuitBreakerStatus>> stateCombo(
            @RequestParam(defaultValue = "externalApi") String primary,
            @RequestParam(defaultValue = "secondaryApi") String secondary
    ) {
        return circuitBreakerService.getCombinedStatus(primary, secondary);
    }

    @GetMapping("/states")
    public Mono<ApiResponse<Set<String>>> states() {
        return circuitBreakerService.listCircuitBreakers();
    }
}

package com.joker.resilience4j.controller;

import com.joker.resilience4j.model.ApiResponse;
import com.joker.resilience4j.service.ProductionService;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/production")
public class ProductionController {

    private final ProductionService productionService;

    public ProductionController(ProductionService productionService) {
        this.productionService = productionService;
    }

    @GetMapping("/test")
    public Mono<ApiResponse<String>> test(
            @RequestParam(defaultValue = "success") String mode,
            @RequestParam(defaultValue = "0") long delayMs
    ) {
        return productionService.callWithFullProtection(mode, delayMs);
    }

    @GetMapping("/cache")
    public Mono<ApiResponse<Map<String, String>>> cache() {
        return productionService.getCacheSnapshot();
    }

    @DeleteMapping("/cache")
    public Mono<ApiResponse<String>> clearCache() {
        return productionService.clearCache();
    }
}

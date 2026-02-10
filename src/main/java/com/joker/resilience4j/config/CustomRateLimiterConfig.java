package com.joker.resilience4j.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CustomRateLimiterConfig {

    @Bean
    public RateLimiterConfig rateLimiterConfig() {
        return RateLimiterConfig.custom()
                .limitForPeriod(10)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build();
    }

    @Bean
    public RateLimiterRegistry rateLimiterRegistry(RateLimiterConfig rateLimiterConfig) {
        return RateLimiterRegistry.of(rateLimiterConfig);
    }

    @Bean
    public RateLimiterFactory rateLimiterFactory(RateLimiterConfig rateLimiterConfig) {
        return new RateLimiterFactory(rateLimiterConfig);
    }

    @Bean
    public RateLimiter rateLimiter(RateLimiterFactory rateLimiterFactory) {
        return rateLimiterFactory.create("externalApi", RateLimiterFactory.ConfigTemplate.BURST);
    }
}

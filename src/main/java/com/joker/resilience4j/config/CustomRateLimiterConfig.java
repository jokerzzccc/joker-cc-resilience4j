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
    public RateLimiterConfig rateLimiterConfig(ResilienceConfigProperties properties) {
        ResilienceConfigProperties.RateLimiterProps rl = properties.rateLimiter();
        return RateLimiterConfig.custom()
                .limitForPeriod(rl.limitForPeriod())
                .limitRefreshPeriod(Duration.ofMillis(rl.limitRefreshPeriodMs()))
                .timeoutDuration(Duration.ofMillis(rl.timeoutDurationMs()))
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

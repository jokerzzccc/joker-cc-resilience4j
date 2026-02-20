package com.joker.resilience4j.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 外部化配置属性，绑定 {@code app.resilience} 前缀。
 *
 * <p>包含 {@link CircuitBreakerProps} 和 {@link RateLimiterProps} 两个嵌套 record，
 * 所有参数均通过 {@link DefaultValue} 提供默认值，无需 YAML 即可启动。</p>
 */
@ConfigurationProperties(prefix = "app.resilience")
public record ResilienceConfigProperties(
        @DefaultValue CircuitBreakerProps circuitBreaker,
        @DefaultValue RateLimiterProps rateLimiter
) {

    public record CircuitBreakerProps(
            @DefaultValue("50") float failureRateThreshold,
            @DefaultValue("50") float slowCallRateThreshold,
            @DefaultValue("2000") int slowCallDurationThresholdMs,
            @DefaultValue("10") int slidingWindowSize,
            @DefaultValue("5") int minimumNumberOfCalls,
            @DefaultValue("2") int permittedNumberOfCallsInHalfOpenState,
            @DefaultValue("5000") int waitDurationInOpenStateMs
    ) {
    }

    public record RateLimiterProps(
            @DefaultValue("10") int limitForPeriod,
            @DefaultValue("1000") int limitRefreshPeriodMs,
            @DefaultValue("0") int timeoutDurationMs
    ) {
    }
}

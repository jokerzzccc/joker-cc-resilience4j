package com.joker.resilience4j.examples.basic.ratelimiter;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import java.time.Duration;

public class BasicRateLimiterExample {

    public static void main(String[] args) {
        // Basic configuration: 2 permits per second, no waiting
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(2)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build();

        RateLimiter rateLimiter = RateLimiter.of("example", config);

        for (int i = 0; i < 5; i++) {
            try {
                String result = rateLimiter.executeSupplier(() -> "ok");
                System.out.println("call=" + i + " result=" + result
                        + " permits=" + rateLimiter.getMetrics().getAvailablePermissions());
            } catch (RequestNotPermitted ex) {
                System.out.println("call=" + i + " rejected"
                        + " permits=" + rateLimiter.getMetrics().getAvailablePermissions());
            }
        }

        System.out.println("availablePermissions=" + rateLimiter.getMetrics().getAvailablePermissions());
    }
}

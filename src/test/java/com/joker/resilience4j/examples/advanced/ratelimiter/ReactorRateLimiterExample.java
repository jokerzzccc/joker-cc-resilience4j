package com.joker.resilience4j.examples.advanced.ratelimiter;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import java.time.Duration;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ReactorRateLimiterExample {

    public static void main(String[] args) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(2)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build();
        RateLimiter rateLimiter = RateLimiter.of("reactor-example", config);

        Mono<String> call = Mono.fromCallable(() -> "ok")
                .subscribeOn(Schedulers.boundedElastic())
                .transformDeferred(RateLimiterOperator.of(rateLimiter))
                .onErrorResume(ex -> Mono.just("fallback"));

        call.repeat(5)
                .doOnNext(result -> System.out.println(
                        "result=" + result
                                + " permits=" + rateLimiter.getMetrics().getAvailablePermissions()))
                .blockLast();
    }
}

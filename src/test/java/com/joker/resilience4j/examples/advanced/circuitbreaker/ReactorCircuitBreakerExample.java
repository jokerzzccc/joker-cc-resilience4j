package com.joker.resilience4j.examples.advanced.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ReactorCircuitBreakerExample {

    public static void main(String[] args) {
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("reactor-example");

        Mono<String> call = Mono.fromCallable(() -> {
                    if (Math.random() < 0.5) {
                        throw new IllegalStateException("boom");
                    }
                    return "ok";
                })
                .subscribeOn(Schedulers.boundedElastic())
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorResume(ex -> Mono.just("fallback"));

        call.repeat(5)
                .doOnNext(result -> System.out.println("result=" + result))
                .blockLast();

        System.out.println("state=" + circuitBreaker.getState());
    }
}

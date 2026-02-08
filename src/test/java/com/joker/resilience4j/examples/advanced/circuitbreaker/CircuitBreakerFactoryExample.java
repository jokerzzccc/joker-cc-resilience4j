package com.joker.resilience4j.examples.advanced.circuitbreaker;

import com.joker.resilience4j.config.CircuitBreakerFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

public class CircuitBreakerFactoryExample {

    public static void main(String[] args) {
        CircuitBreakerFactory factory = new CircuitBreakerFactory(CircuitBreakerConfig.ofDefaults());

        CircuitBreaker fast = factory.create("fast", CircuitBreakerFactory.ConfigTemplate.FAST_FAIL);
        CircuitBreaker slow = factory.create("slow", CircuitBreakerFactory.ConfigTemplate.SLOW_CALL);
        CircuitBreaker hybrid = factory.create("hybrid", CircuitBreakerFactory.ConfigTemplate.HYBRID);

        System.out.println("fast=" + fast.getName());
        System.out.println("slow=" + slow.getName());
        System.out.println("hybrid=" + hybrid.getName());
        System.out.println("names=" + factory.listNames());
    }
}

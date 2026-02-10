package com.joker.resilience4j.examples.advanced.ratelimiter;

import com.joker.resilience4j.config.RateLimiterFactory;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;

public class RateLimiterFactoryExample {

    public static void main(String[] args) {
        RateLimiterFactory factory = new RateLimiterFactory(RateLimiterConfig.ofDefaults());

        RateLimiter strict = factory.create("strict", RateLimiterFactory.ConfigTemplate.STRICT);
        RateLimiter lenient = factory.create("lenient", RateLimiterFactory.ConfigTemplate.LENIENT);
        RateLimiter burst = factory.create("burst", RateLimiterFactory.ConfigTemplate.BURST);

        System.out.println("strict=" + strict.getName());
        System.out.println("lenient=" + lenient.getName());
        System.out.println("burst=" + burst.getName());
        System.out.println("names=" + factory.listNames());
    }
}

package com.joker.resilience4j;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Resilience4j Reactor Learning Application
 *
 * 本项目专注于学习 Resilience4j 2.3 基于 Reactor 的编程式 API
 *
 * 核心特性：
 * - 使用 resilience4j-reactor 模块
 * - 通过 transformDeferred 操作符集成 CircuitBreaker 和 RateLimiter
 * - 不使用 Spring Boot 注解方式（@CircuitBreaker, @RateLimiter）
 * - 全链路响应式编程（Mono/Flux）
 *
 * @author Joker
 * @version 1.0.0
 */
@SpringBootApplication
public class Application {

    private static ConfigurableApplicationContext context;

    public static void main(String[] args) {
        run(args);
    }

    static ConfigurableApplicationContext run(String... args) {
        context = SpringApplication.run(Application.class, args);
        return context;
    }

    static void stop() {
        if (context != null) {
            context.close();
            context = null;
        }
    }
}

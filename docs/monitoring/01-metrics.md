# 指标收集

## 1. Micrometer 核心概念

Micrometer 是 Java 指标收集的门面库（类比 SLF4J 之于日志），提供统一 API 对接各种监控系统。

| 概念 | 说明 | 典型用途 |
|------|------|---------|
| `MeterRegistry` | 指标注册中心 | Spring Boot 自动配置，无需手动创建 |
| `Counter` | 单调递增计数器 | 请求总数、错误总数 |
| `Timer` | 计时器（含计数） | 接口响应时间、调用耗时 |
| `Gauge` | 瞬时值指标 | 当前连接数、可用许可数 |

---

## 2. resilience4j-micrometer 模块

`resilience4j-micrometer` 提供 `Tagged*Metrics` 类，将 Registry 中的实例自动绑定到 Micrometer：

```java
// 绑定 CircuitBreakerRegistry —— 自动跟踪 Registry 内所有实例
TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry);

// 绑定 RateLimiterRegistry
TaggedRateLimiterMetrics.ofRateLimiterRegistry(rateLimiterRegistry);
```

这些绑定器实现了 `MeterBinder` 接口，Spring Boot 会自动调用 `bindTo(MeterRegistry)` 完成注册。

---

## 3. CircuitBreaker 指标

| Prometheus 指标名 | 类型 | 含义 |
|-------------------|------|------|
| `resilience4j_circuitbreaker_state` | Gauge | 熔断器状态（0=CLOSED, 1=OPEN, 2=HALF_OPEN） |
| `resilience4j_circuitbreaker_failure_rate` | Gauge | 当前失败率（%） |
| `resilience4j_circuitbreaker_slow_call_rate` | Gauge | 当前慢调用率（%） |
| `resilience4j_circuitbreaker_buffered_calls` | Gauge | 滑动窗口内缓冲调用数 |
| `resilience4j_circuitbreaker_failed_calls` | Gauge | 滑动窗口内失败调用数 |
| `resilience4j_circuitbreaker_not_permitted_calls` | Gauge | 被拒绝的调用数（OPEN 状态） |
| `resilience4j_circuitbreaker_calls_seconds_count` | Counter | 调用总次数（Tagged 自动注册） |
| `resilience4j_circuitbreaker_calls_seconds_sum` | Counter | 调用总耗时（Tagged 自动注册） |

---

## 4. RateLimiter 指标

| Prometheus 指标名 | 类型 | 含义 |
|-------------------|------|------|
| `resilience4j_ratelimiter_available_permissions` | Gauge | 当前可用许可数 |
| `resilience4j_ratelimiter_waiting_threads` | Gauge | 等待许可的线程数 |

---

## 5. MetricsConfig 实现

```java
@Configuration
public class MetricsConfig {

    // Registry 级别绑定 —— 标准 resilience4j-micrometer 模式
    @Bean
    public MeterBinder circuitBreakerMetrics(CircuitBreakerRegistry registry) {
        return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry);
    }

    @Bean
    public MeterBinder rateLimiterMetrics(RateLimiterRegistry registry) {
        return TaggedRateLimiterMetrics.ofRateLimiterRegistry(registry);
    }

    // 自定义业务计数器
    @Bean
    public Counter circuitBreakerCallCounter(MeterRegistry meterRegistry) {
        return Counter.builder("resilience4j.custom.circuitbreaker.calls.total")
                .description("Total CircuitBreaker calls through the service layer")
                .tag("component", "circuitbreaker")
                .register(meterRegistry);
    }

    @Bean
    public Counter rateLimiterCallCounter(MeterRegistry meterRegistry) {
        return Counter.builder("resilience4j.custom.ratelimiter.calls.total")
                .tag("component", "ratelimiter")
                .register(meterRegistry);
    }

    // 自定义计时器
    @Bean
    public Timer apiCallTimer(MeterRegistry meterRegistry) {
        return Timer.builder("resilience4j.custom.api.call.duration")
                .tag("component", "api")
                .register(meterRegistry);
    }

    // Factory 实例级别 Gauge 注册
    @Bean
    public ResilienceMetricsRegistrar resilienceMetricsRegistrar(
            MeterRegistry meterRegistry,
            CircuitBreaker circuitBreaker,
            RateLimiter rateLimiter) {
        return new ResilienceMetricsRegistrar(meterRegistry, circuitBreaker, rateLimiter);
    }
}
```

---

## 6. ResilienceMetricsRegistrar 设计

**为什么需要 ResilienceMetricsRegistrar？**

项目使用 Factory 模式管理 Resilience4j 实例（`CircuitBreakerFactory`、`RateLimiterFactory`），Factory 内部用 `ConcurrentHashMap` 缓存实例，绕过了 Registry。`TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry)` 只能监控 Registry 内的实例。

`ResilienceMetricsRegistrar` 补充了这个缺口，为 Factory 管理的实例手动注册 Gauge：

```java
public class ResilienceMetricsRegistrar {

    public ResilienceMetricsRegistrar(
            MeterRegistry meterRegistry,
            CircuitBreaker circuitBreaker,
            RateLimiter rateLimiter) {
        this.meterRegistry = meterRegistry;
        registerCircuitBreakerGauges(circuitBreaker);
        registerRateLimiterGauges(rateLimiter);
    }

    public void registerCircuitBreakerGauges(CircuitBreaker cb) {
        Gauge.builder("resilience4j.circuitbreaker.state", cb,
                        c -> c.getState().getOrder())
                .tag("name", cb.getName())
                .register(meterRegistry);
        // ... 6 个 Gauge
    }

    public void registerRateLimiterGauges(RateLimiter rl) {
        Gauge.builder("resilience4j.ratelimiter.available.permissions", rl,
                        r -> r.getMetrics().getAvailablePermissions())
                .tag("name", rl.getName())
                .register(meterRegistry);
        // ... 2 个 Gauge
    }
}
```

**Gauge lambda 实时读取**：每次 Prometheus 抓取时，lambda 函数被调用，返回 resilience4j 实例的最新 Metrics 值。

---

## 7. 最佳实践

- **命名规范**：使用 `.` 分隔的层级名称（如 `resilience4j.circuitbreaker.state`），Prometheus 会自动转换为 `_`
- **Tag 策略**：用 `name` tag 区分不同实例，避免为每个实例创建独立指标名
- **基数控制**：Tag 值应有限且可枚举，避免用户 ID 等高基数值作为 tag
- **双层绑定**：Registry 级别（自动发现新实例）+ 实例级别（精确控制已知实例）

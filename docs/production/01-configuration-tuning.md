# 配置调优指南

## 概述

Resilience4j 的配置直接影响系统的容错行为。本文档介绍如何根据不同场景和环境调优配置参数。

---

## 外部化配置

本项目通过 `ResilienceConfigProperties` 将 Resilience4j 参数外部化到 YAML，实现多环境差异化配置。

### 配置属性类

```java
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
    ) {}
    // ...
}
```

### 多环境配置

| 参数 | 开发环境 | 生产环境 | 说明 |
|------|---------|---------|------|
| `failureRateThreshold` | 80% | 30% | 生产更敏感 |
| `slidingWindowSize` | 5 | 100 | 生产需要更大样本 |
| `minimumNumberOfCalls` | 2 | 20 | 避免少量请求误触发 |
| `waitDurationInOpenStateMs` | 2000 | 30000 | 生产恢复更谨慎 |
| `limitForPeriod` | 100 | 50 | 生产限流更严格 |

**application-dev.yml**:
```yaml
app:
  resilience:
    circuit-breaker:
      failure-rate-threshold: 80
      sliding-window-size: 5
      wait-duration-in-open-state-ms: 2000
    rate-limiter:
      limit-for-period: 100
      timeout-duration-ms: 500
```

**application-prod.yml**:
```yaml
app:
  resilience:
    circuit-breaker:
      failure-rate-threshold: 30
      sliding-window-size: 100
      minimum-number-of-calls: 20
      wait-duration-in-open-state-ms: 30000
    rate-limiter:
      limit-for-period: 50
      timeout-duration-ms: 0
```

---

## CircuitBreaker 配置调优

### 场景一：微服务内部调用

内部服务间延迟低、失败率预期低，应快速检测异常：

```java
CircuitBreakerConfig.custom()
    .failureRateThreshold(50)
    .slowCallRateThreshold(80)
    .slowCallDurationThreshold(Duration.ofMillis(500))
    .slidingWindowSize(20)
    .minimumNumberOfCalls(5)
    .waitDurationInOpenState(Duration.ofSeconds(10))
    .build();
```

### 场景二：第三方 API 调用

外部 API 延迟不可控，需要更宽容的慢调用阈值：

```java
CircuitBreakerConfig.custom()
    .failureRateThreshold(30)
    .slowCallRateThreshold(50)
    .slowCallDurationThreshold(Duration.ofSeconds(3))
    .slidingWindowSize(50)
    .minimumNumberOfCalls(10)
    .waitDurationInOpenState(Duration.ofSeconds(30))
    .build();
```

### 场景三：数据库访问

数据库故障影响面大，需要大窗口和谨慎恢复：

```java
CircuitBreakerConfig.custom()
    .failureRateThreshold(50)
    .slidingWindowSize(100)
    .minimumNumberOfCalls(20)
    .permittedNumberOfCallsInHalfOpenState(5)
    .waitDurationInOpenState(Duration.ofSeconds(60))
    .build();
```

---

## RateLimiter 配置调优

### 关键参数

| 参数 | 作用 | 调优建议 |
|------|------|---------|
| `limitForPeriod` | 每个周期允许的请求数 | 根据下游承载能力设置 |
| `limitRefreshPeriod` | 许可刷新周期 | 通常 1 秒，突发场景可缩短 |
| `timeoutDuration` | 等待许可的超时时间 | 0 = 立即拒绝，适合高吞吐 |

### 严格限流（API 网关）

```java
RateLimiterConfig.custom()
    .limitForPeriod(50)
    .limitRefreshPeriod(Duration.ofSeconds(1))
    .timeoutDuration(Duration.ZERO) // 立即拒绝
    .build();
```

### 宽松限流（内部服务）

```java
RateLimiterConfig.custom()
    .limitForPeriod(500)
    .limitRefreshPeriod(Duration.ofSeconds(1))
    .timeoutDuration(Duration.ofMillis(500)) // 允许短暂等待
    .build();
```

---

## 动态配置更新

通过 `ConfigController` 端点在运行时更新配置：

```bash
# 更新 CircuitBreaker 配置
curl -X POST "http://localhost:8080/api/config/circuit-breaker/externalApi?\
failureRateThreshold=30&slidingWindowSize=50"

# 查看当前配置
curl http://localhost:8080/api/config/circuit-breaker/externalApi

# 更新 RateLimiter 配置
curl -X POST "http://localhost:8080/api/config/rate-limiter/externalApi?\
limitForPeriod=100&timeoutDurationMs=500"
```

**注意**：动态更新会创建新的 Resilience4j 实例，之前的指标数据会重置。

---

## 调优原则

1. **从宽松到严格**：上线初期用宽松配置，根据监控数据逐步收紧
2. **slidingWindowSize 要足够大**：太小会导致统计不准确
3. **minimumNumberOfCalls 不能太小**：避免少量请求就触发熔断
4. **waitDurationInOpenState 要合理**：太短导致频繁试探，太长导致恢复慢
5. **timeoutDuration = 0**：高吞吐场景建议立即拒绝，不要让请求排队

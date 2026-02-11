# 性能优化指南

## 概述

Resilience4j 设计轻量，开销极小。本文档介绍性能相关的配置要点和 Reactor 集成的最佳实践。

---

## Resilience4j 性能特点

### CircuitBreaker

- **滑动窗口**：基于 Ring Bit Buffer 实现，内存占用 O(n)，n 为窗口大小
- **状态检查**：原子操作，无锁竞争
- **指标统计**：每次调用结束时同步更新

**影响因素**：
| 参数 | 性能影响 | 建议 |
|------|---------|------|
| `slidingWindowSize` | 窗口越大，内存占用越大 | 生产建议 10-100 |
| `slidingWindowType` | COUNT_BASED 比 TIME_BASED 开销更小 | 默认 COUNT_BASED 即可 |
| `minimumNumberOfCalls` | 无直接性能影响 | 根据业务设置 |

### RateLimiter

- **AtomicRateLimiter**：基于原子操作和 `System.nanoTime()`，无锁
- **SemaphoreBasedRateLimiter**：基于 Java Semaphore，有轻微锁开销

**建议**：默认使用 AtomicRateLimiter（Resilience4j 默认实现）。

---

## Reactor 集成性能

### transformDeferred 开销

`transformDeferred` 本身是零开销操作，它只是延迟将操作符应用到订阅时：

```java
// 性能等价于直接调用
source.transformDeferred(CircuitBreakerOperator.of(cb))
```

### 调度器选择

| 场景 | 调度器 | 原因 |
|------|-------|------|
| 非阻塞调用 | 默认（event loop） | 无需切换线程 |
| 阻塞调用 | `Schedulers.boundedElastic()` | 避免阻塞 event loop |
| CPU 密集 | `Schedulers.parallel()` | 利用多核 |

```java
// 阻塞操作必须切换调度器
Mono.fromCallable(() -> blockingCall())
    .subscribeOn(Schedulers.boundedElastic())
    .transformDeferred(CircuitBreakerOperator.of(cb));
```

**关键原则**：永远不要在 Reactor 的 event loop 线程上执行阻塞操作。

### 操作符顺序

RateLimiter 放在 CircuitBreaker 前面：

```java
source
    .transformDeferred(RateLimiterOperator.of(rl))   // 先限流
    .transformDeferred(CircuitBreakerOperator.of(cb)) // 后熔断
```

**原因**：
1. 被限流拒绝的请求不会计入 CircuitBreaker 的窗口
2. 减少对下游的无效调用
3. 限流检查比实际调用开销小得多

---

## 指标收集性能

### Micrometer 开销

- Counter/Timer 的 `increment()`/`record()` 是原子操作，开销极小
- Gauge 的 lambda 仅在 Prometheus 抓取时执行（通常 15-30 秒一次）
- Tag 数量影响内存：每个唯一 tag 组合对应一个时间序列

**建议**：
- 控制 tag 基数（避免用 userId 等高基数值作为 tag）
- Prometheus 抓取间隔不要太短（推荐 15s）

### Factory 实例指标

`ResilienceMetricsRegistrar` 为 Factory 管理的实例手动注册 Gauge：

```java
Gauge.builder("resilience4j.circuitbreaker.state", cb,
    c -> c.getState().getOrder())
    .register(meterRegistry);
```

每个 Gauge 的 lambda 在每次抓取时执行，确保 lambda 内的操作是轻量的。

---

## 配置对性能的影响

### CircuitBreaker

| 配置 | 性能影响 |
|------|---------|
| 大 `slidingWindowSize` (>100) | 内存增加，但计算开销不变 |
| 短 `waitDurationInOpenState` | 频繁状态转换，增加日志量 |
| 启用 `recordExceptions` 过滤 | 每次异常需要类型检查 |

### RateLimiter

| 配置 | 性能影响 |
|------|---------|
| `timeoutDuration > 0` | 请求可能排队等待，增加延迟 |
| `timeoutDuration = 0` | 立即拒绝，延迟最低 |
| 小 `limitRefreshPeriod` | 刷新更频繁，原子操作开销略增 |

---

## 性能优化清单

1. **避免阻塞 event loop**：阻塞操作使用 `subscribeOn(Schedulers.boundedElastic())`
2. **RateLimiter 前置**：先限流后熔断，减少无效调用
3. **timeoutDuration = 0**：高吞吐场景立即拒绝
4. **合理窗口大小**：slidingWindowSize 10-100，避免过大
5. **控制 tag 基数**：Micrometer tag 值不要用高基数字段
6. **日志级别**：生产环境 Resilience4j 日志设为 INFO，避免 DEBUG 日志拖慢性能
7. **缓存降级**：使用内存缓存而非每次都调用外部服务降级

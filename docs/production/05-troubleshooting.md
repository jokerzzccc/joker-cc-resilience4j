# 故障排查指南

## 概述

本文档汇总 Resilience4j + Reactor 集成中常见的问题和排查方法。

---

## 常见问题

### 1. CircuitBreaker 意外打开

**现象**：服务正常但 CircuitBreaker 进入 OPEN 状态。

**排查步骤**：
```bash
# 1. 查看 CircuitBreaker 状态
curl http://localhost:8080/api/circuit-breaker/state?name=externalApi

# 2. 查看当前配置
curl http://localhost:8080/api/config/circuit-breaker/externalApi

# 3. 查看 Prometheus 指标
curl http://localhost:8080/actuator/prometheus | grep circuitbreaker
```

**常见原因**：
| 原因 | 解决方案 |
|------|---------|
| `minimumNumberOfCalls` 太小 | 增大到 10-20 |
| `failureRateThreshold` 太低 | 根据正常错误率上调 |
| `slidingWindowSize` 太小 | 增大到 20-100 |
| 慢调用被计为失败 | 调整 `slowCallDurationThreshold` |

### 2. RateLimiter 过度拒绝

**现象**：请求被大量拒绝，但实际流量未超限。

**排查步骤**：
```bash
# 查看 RateLimiter 状态
curl http://localhost:8080/api/rate-limiter/state?name=externalApi

# 查看可用许可数
curl http://localhost:8080/actuator/prometheus | grep ratelimiter
```

**常见原因**：
| 原因 | 解决方案 |
|------|---------|
| `limitForPeriod` 设置过小 | 根据实际 QPS 调整 |
| `limitRefreshPeriod` 过长 | 缩短到 1 秒 |
| `timeoutDuration = 0` 导致立即拒绝 | 适当增加等待时间 |
| 多个服务实例共享同一个限流器名称 | 每个实例独立限流，总量需乘以实例数 |

### 3. 熔断器不恢复

**现象**：CircuitBreaker 保持 OPEN 状态，不进入 HALF_OPEN。

**排查步骤**：
```bash
# 查看状态
curl http://localhost:8080/api/circuit-breaker/state?name=externalApi

# 查看 waitDurationInOpenState 配置
curl http://localhost:8080/api/config/circuit-breaker/externalApi
```

**常见原因**：
| 原因 | 解决方案 |
|------|---------|
| `waitDurationInOpenState` 设置过长 | 缩短等待时间 |
| HALF_OPEN 试探请求仍然失败 | 排查下游服务是否真的恢复 |
| 无请求触发状态转换 | CircuitBreaker 需要有请求进来才会检查是否该从 OPEN 转 HALF_OPEN |

### 4. Reactor 线程阻塞

**现象**：响应变慢，event loop 线程出现阻塞警告。

**排查方法**：
```
日志中出现：
"Scheduler worker in a]actory-1 has been blocked for X ms"
```

**解决方案**：
```java
// 阻塞操作必须切到 boundedElastic
Mono.fromCallable(() -> blockingDatabaseCall())
    .subscribeOn(Schedulers.boundedElastic())
    .transformDeferred(CircuitBreakerOperator.of(cb));
```

### 5. 动态更新后指标丢失

**现象**：通过 ConfigController 更新配置后，Prometheus 指标归零。

**原因**：`Factory.update()` 创建新的 Resilience4j 实例，旧实例的指标不再更新。

**解决方案**：这是预期行为。更新后的新实例会重新开始采集指标。如需保留历史数据，应在 Grafana 中使用 `increase()` 或 `rate()` 函数而非绝对值。

### 6. JaCoCo 覆盖率不足

**现象**：Gauge lambda 函数未被覆盖。

**原因**：JaCoCo 将 lambda 视为独立方法，仅注册 Gauge 不够，必须调用 `gauge.value()` 才能触发 lambda 执行。

**解决方案**：
```java
// 测试中调用 gauge.value() 触发 lambda
Gauge gauge = meterRegistry.find("resilience4j.circuitbreaker.state").gauge();
assertThat(gauge).isNotNull();
assertThat(gauge.value()).isGreaterThanOrEqualTo(0);
```

---

## 诊断端点汇总

| 端点 | 用途 |
|------|------|
| `GET /api/circuit-breaker/state?name=X` | 查看 CB 状态和指标 |
| `GET /api/rate-limiter/state?name=X` | 查看 RL 状态和许可数 |
| `GET /api/config/circuit-breaker/{name}` | 查看 CB 当前配置 |
| `GET /api/config/rate-limiter/{name}` | 查看 RL 当前配置 |
| `GET /api/production/cache` | 查看降级缓存内容 |
| `GET /api/metrics/snapshot` | 查看自定义指标快照 |
| `GET /actuator/prometheus` | Prometheus 指标导出 |
| `GET /actuator/health` | 健康检查 |
| `GET /actuator/metrics` | Micrometer 指标列表 |

---

## 日志关键字

排查时可搜索以下日志关键字：

```
# CircuitBreaker 状态转换
grep "transition" app.log

# CircuitBreaker 失败率超阈值
grep "failureRateExceeded" app.log

# CircuitBreaker 调用被拒
grep "callNotPermitted" app.log

# RateLimiter 请求被拒
grep "rejected" app.log

# 全局异常处理
grep "Unhandled exception" app.log
```

---

## 故障处理流程

```
1. 检查健康状态：GET /actuator/health
2. 检查组件状态：GET /api/circuit-breaker/state / /api/rate-limiter/state
3. 查看配置：GET /api/config/circuit-breaker/{name} / /api/config/rate-limiter/{name}
4. 查看指标：GET /actuator/prometheus
5. 查看日志：搜索关键字 transition / rejected / callNotPermitted
6. 必要时动态调整：POST /api/config/circuit-breaker/{name}
7. 清除降级缓存：DELETE /api/production/cache
```

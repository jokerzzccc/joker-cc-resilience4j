# Resilience4j 2.3 Reactor 学习总结

## 项目概述

| 指标 | 数据 |
|------|------|
| 学习周期 | 2026-01-31 ~ 2026-02-20 |
| 完成阶段 | 8 / 8（100%） |
| Java 源文件 | 25 个（config 8 + controller 5 + service 4 + model 7 + app 1） |
| 单元测试 | 133 个，全部通过 |
| JaCoCo 覆盖率 | LINE 100%，BRANCH 100% |
| 教学文档 | 20 份 |
| 示例代码 | 8 个可运行 Example 类 |

---

## 各阶段核心收获

### 阶段一：项目基础搭建

- Spring Boot 3.3 + WebFlux + JDK 21 项目骨架
- `resilience4j-reactor` 2.3.0 作为核心依赖，不使用 `resilience4j-spring-boot3` 自动配置

### 阶段二：CircuitBreaker 基础

- 三种状态：CLOSED → OPEN → HALF_OPEN → CLOSED
- 核心参数：`failureRateThreshold`、`slidingWindowSize`、`waitDurationInOpenState`、`minimumNumberOfCalls`
- `CircuitBreaker.of(name, config)` 编程式创建

### 阶段三：CircuitBreaker + Reactor 集成

- **核心模式**：`source.transformDeferred(CircuitBreakerOperator.of(cb))` 将 Mono/Flux 纳入熔断保护
- **工厂模式**：`CircuitBreakerFactory` 提供 FAST_FAIL / SLOW_CALL / HYBRID 三种模板，ConcurrentHashMap 缓存实例
- 事件监听：`getEventPublisher().onStateTransition() / onFailureRateExceeded() / onSlowCallRateExceeded() / onCallNotPermitted()`

### 阶段四：RateLimiter 基础

- 两种实现：AtomicRateLimiter（默认）和 SemaphoreBasedRateLimiter
- 核心参数：`limitForPeriod`、`limitRefreshPeriod`、`timeoutDuration`
- `registry.find(name)` 返回 `Optional`，`registry.rateLimiter(name)` 自动创建 — 注意区别

### 阶段五：RateLimiter + Reactor 集成

- **核心模式**：`source.transformDeferred(RateLimiterOperator.of(rl))`
- **工厂模式**：`RateLimiterFactory` 提供 STRICT / LENIENT / BURST 三种模板
- RateLimiter 事件仅有 `onSuccess` / `onFailure`（比 CircuitBreaker 的 4 种事件更简单）
- `resolveRateLimiter` 自动创建模式：找不到时用默认模板创建

### 阶段六：监控和指标体系

- **双层指标绑定**：Registry 级别 `TaggedCircuitBreakerMetrics` + Factory 级别 `ResilienceMetricsRegistrar`
- Factory 管理的实例绕过 Registry，需要手动注册 Gauge
- Micrometer Gauge 的 lambda 在每次 Prometheus 抓取时被调用，实时返回最新值
- JaCoCo 将 lambda 视为独立方法，必须实际调用 `gauge.value()` 才能覆盖

### 阶段七：生产环境最佳实践

- `@ConfigurationProperties` + record 原生支持构造器绑定，`@DefaultValue` 提供默认值
- `@ConfigurationPropertiesScan` 必须加在 Application 类上
- 多环境配置：dev（宽松阈值、DEBUG 日志）/ prod（严格阈值、INFO 日志）
- `Factory.update()` 创建新实例替换缓存，指标会重置
- **操作符顺序**：RateLimiter 放在 CircuitBreaker 前面，限流拒绝不计入熔断窗口
- 多级降级：`doOnNext` 缓存成功结果 → `onErrorResume` 按异常类型分级处理

### 阶段八：文档完善和总结

- 补充所有公开 API 类的 JavaDoc
- 修复 JaCoCo 分支覆盖率缺口（ProductionService 的 `RequestNotPermitted` 无缓存分支）
- 编写 API 参考文档和学习总结

---

## 遇到的问题与解决方案

| # | 问题 | 解决方案 |
|---|------|---------|
| 1 | `ErrorResponse.circuitBreakerState` 与 CB 耦合，RL 无法复用 | 重命名为 `componentState` |
| 2 | `registry.rateLimiter(name)` 自动创建实例，"未找到"分支无法覆盖 | 改用 `registry.find(name)` 返回 `Optional` |
| 3 | `ApiResponse::success` 方法引用歧义（record accessor vs static factory） | 改用 lambda `response -> response.success()` |
| 4 | Gauge lambda 未被 JaCoCo 覆盖 | 测试中调用 `gauge.value()` 触发 lambda 执行 |
| 5 | `cb.onSuccess(0, null)` 导致 NPE | 改用 `cb.onSuccess(0, TimeUnit.MILLISECONDS)` |
| 6 | JaCoCo BRANCH 覆盖率 90%（`||` 条件未完全覆盖） | 新增 `shouldReturnStaticFallbackWhenRateLimitedAndNoCache` 测试 |

---

## Resilience4j + Reactor 最佳实践清单

### 集成模式

1. **始终使用 `transformDeferred`**，而非 `transform` — 前者在订阅时应用保护，后者在组装时应用
2. **操作符顺序**：`.transformDeferred(RateLimiterOperator.of(rl)).transformDeferred(CircuitBreakerOperator.of(cb))` — 限流在外，熔断在内
3. **阻塞调用必须 `subscribeOn(Schedulers.boundedElastic())`** — 避免阻塞 Netty 事件循环线程

### 配置建议

4. **`minimumNumberOfCalls`** 设置合理值 — 太低会导致误熔断
5. **`timeoutDuration = Duration.ZERO`** — 生产环境不推荐等待，快速拒绝优先
6. **`waitDurationInOpenState`** 根据下游恢复时间设置 — 不宜过短（频繁探测）或过长（恢复慢）

### 工厂模式

7. **用 Factory 替代直接 `CircuitBreaker.of()`** — 统一管理、避免重复创建、支持动态更新
8. **预定义配置模板** — 减少重复配置代码，按场景选择（快速失败 / 慢调用检测 / 混合）
9. **`computeIfAbsent`** 保证线程安全的单例创建

### 降级策略

10. **多级降级**：缓存 → 静态兜底 → 错误响应 — 逐级退化而非直接失败
11. **`doOnNext` 缓存成功结果** — 为后续降级提供数据源
12. **按异常类型分级处理**：`CallNotPermittedException` / `RequestNotPermitted` / 通用异常

### 监控

13. **Factory 实例需手动注册 Gauge** — `TaggedCircuitBreakerMetrics` 只覆盖 Registry 中的实例
14. **自定义 Counter/Timer** 补充业务维度指标 — Resilience4j 内置指标偏底层

### 测试

15. **JaCoCo lambda 覆盖**：Gauge 的 lambda 必须通过 `gauge.value()` 实际调用才算覆盖
16. **`||` 条件的所有分支** 都需要独立测试用例

---

## 后续学习方向

1. **Resilience4j 其他模块**：Retry、Bulkhead、TimeLimiter
2. **源码阅读**：`CircuitBreakerStateMachine`、`AtomicRateLimiter` 实现原理
3. **分布式场景**：结合 Redis 实现分布式限流
4. **实际项目应用**：将本项目模式应用到微服务网关和 RPC 调用

---

**版本**: v1.0
**最后更新**: 2026-02-20

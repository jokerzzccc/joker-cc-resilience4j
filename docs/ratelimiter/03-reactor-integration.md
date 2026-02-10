# RateLimiter + Reactor 集成

## 目标
- 理解 `transformDeferred` 与 `RateLimiterOperator` 的集成方式
- 使用 `transformDeferred` 完成 Reactor 集成
- 统一响应结构与错误处理
- 引入 fallback 策略
- 展示组合限流器的链式使用

---

## 1. 为什么用 transformDeferred 而不是 transform

| 特性 | `transform` | `transformDeferred` |
|------|-------------|---------------------|
| 执行时机 | 组装时（assembly time）执行一次 | 每次订阅时（subscription time）执行 |
| 状态捕获 | 捕获组装时的状态 | 每次订阅获取最新状态 |
| 适用场景 | 无状态转换 | 有状态转换（如限流器） |

RateLimiter 是有状态组件（可用许可数持续变化），必须在每次订阅时检查当前许可状态决定是否放行。如果用 `transform`，许可状态会被固化在组装时刻。

```java
// 错误：transform 只在组装时获取一次限流状态
mono.transform(RateLimiterOperator.of(rateLimiter));

// 正确：transformDeferred 每次订阅时重新检查许可
mono.transformDeferred(RateLimiterOperator.of(rateLimiter));
```

`RateLimiterOperator.of()` 返回 `UnaryOperator<Publisher<T>>`，每次被 `transformDeferred` 调用时会请求许可，无许可则抛出 `RequestNotPermitted`。

---

## 2. 基础集成

```java
private Mono<ApiResponse<String>> callWithRateLimiter(
        String name, String mode, long delayMs, boolean useFallback) {
    RateLimiter selected = resolveRateLimiter(name);
    CallPlan plan = selectSupplier(mode, delayMs);
    Mono<String> source = Mono.fromCallable(plan.supplier()::get);
    if (plan.blocking()) {
        source = source.subscribeOn(Schedulers.boundedElastic());
    }

    Mono<String> guarded = source.transformDeferred(RateLimiterOperator.of(selected));
    // ...
}
```

**要点**：

1. **`Mono.fromCallable`** — 将同步调用包装为惰性 Mono，确保调用发生在订阅时
2. **`subscribeOn(Schedulers.boundedElastic())`** — 阻塞调用（如 slow 模式）必须切换到弹性线程池，避免阻塞 Netty 事件循环
3. **`transformDeferred`** — 每次订阅时请求许可，无许可直接抛出 `RequestNotPermitted`
4. **`onErrorResume`** — 捕获所有异常统一转为 `ApiResponse` 结构

与 CircuitBreaker 的关键区别：CircuitBreaker 检查的是熔断器状态（OPEN/CLOSED/HALF_OPEN），RateLimiter 检查的是可用许可数（availablePermissions）。

---

## 3. Fallback 降级策略

```java
Mono<String> guarded = source.transformDeferred(RateLimiterOperator.of(selected));
if (useFallback) {
    return guarded
            .map(ApiResponse::success)
            .onErrorResume(throwable -> Mono.just(ApiResponse.success(
                    "fallback-" + selected.getName()
            )));
}

return guarded
        .map(ApiResponse::success)
        .onErrorResume(throwable -> Mono.just(ApiResponse.failure(
                ErrorResponse.from(throwable, "RATE_LIMITED")
        )));
```

**降级触发条件**：
- `RequestNotPermitted` — 限流器无可用许可，调用被直接拒绝
- 业务异常（如 `IllegalStateException`）— 外部调用本身失败

**降级策略选型**：

| 策略 | 适用场景 | 示例 |
|------|---------|------|
| 静态默认值 | 数据不敏感，可接受默认 | `"fallback-{name}"` |
| 缓存数据 | 读多写少，容忍过期 | 从 Redis 取上次成功结果 |
| 排队等待 | 允许延迟，不能丢弃 | 设置 timeoutDuration > 0 |
| 空响应 | 非关键功能 | 返回空列表 |

本项目使用静态默认值方案，生产环境建议根据业务场景选择。

---

## 4. 组合限流器（链式保护）

```java
return source
        .transformDeferred(RateLimiterOperator.of(primary))
        .transformDeferred(RateLimiterOperator.of(secondary))
        .map(ApiResponse::success)
        .onErrorResume(throwable -> Mono.just(ApiResponse.failure(
                ErrorResponse.from(throwable, "RATE_LIMITED")
        )));
```

**执行顺序**：

```
订阅方向（从外到内）：Subscriber → secondary RL → primary RL → Source
数据流向（从内到外）：Source → primary RL → secondary RL → Subscriber
```

- 订阅时 secondary 先请求许可，再请求 primary 许可
- 如果 secondary 无许可，`RequestNotPermitted` 立即抛出，primary 和 source 都不执行
- 如果 secondary 允许但 primary 无许可，primary 抛出异常
- 如果都允许，source 执行

**应用场景**：全局限流器（如 API 网关级别 100 QPS）+ 细粒度限流器（如单接口 10 QPS）。

---

## 5. REST 接口

| 接口 | 说明 |
|------|------|
| `GET /api/rate-limiter/test?mode=success&delayMs=0` | 基础调用（无降级） |
| `GET /api/rate-limiter/test-fallback?mode=failure` | 带降级的调用 |
| `GET /api/rate-limiter/test-reactor?name=externalApi&mode=success` | 指定限流器名称调用 |
| `GET /api/rate-limiter/test-combo?primary=a&secondary=b&mode=success` | 组合限流器调用 |
| `GET /api/rate-limiter/state?name=externalApi` | 查询单个限流器状态 |
| `GET /api/rate-limiter/state-combo?primary=a&secondary=b` | 查询组合状态 |
| `GET /api/rate-limiter/states` | 列出所有限流器名称 |

**mode 参数**：`success`（默认）、`failure`（模拟异常）、`slow`（模拟慢调用）

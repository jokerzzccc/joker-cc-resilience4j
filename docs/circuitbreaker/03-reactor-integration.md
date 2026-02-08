# CircuitBreaker + Reactor 集成

## 目标
- 理解 `transformDeferred` 与 `transform` 的区别
- 使用 `transformDeferred` 完成 Reactor 集成
- 统一响应结构与错误处理
- 引入 fallback 策略
- 展示组合熔断器的链式使用

---

## 1. 为什么用 transformDeferred 而不是 transform

`transform` 和 `transformDeferred` 都能对 `Mono`/`Flux` 进行转换，但语义不同：

| 特性 | `transform` | `transformDeferred` |
|------|-------------|---------------------|
| 执行时机 | 组装时（assembly time）执行一次 | 每次订阅时（subscription time）执行 |
| 状态捕获 | 捕获组装时的状态 | 每次订阅获取最新状态 |
| 适用场景 | 无状态转换 | 有状态转换（如熔断器） |

CircuitBreaker 是有状态组件（CLOSED → OPEN → HALF_OPEN），必须在每次订阅时检查当前状态决定是否放行。如果用 `transform`，熔断器状态会被固化在组装时刻，后续状态变化不会生效。

```java
// 错误：transform 只在组装时获取一次熔断器状态
mono.transform(CircuitBreakerOperator.of(circuitBreaker));

// 正确：transformDeferred 每次订阅时重新检查状态
mono.transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
```

`CircuitBreakerOperator.of()` 返回的是 `UnaryOperator<Publisher<T>>`，每次被 `transformDeferred` 调用时会创建新的订阅包装，检查当前熔断器状态。

---

## 2. 基础集成

```java
public Mono<ApiResponse<String>> callExternal(String mode, long delayMs) {
    CallPlan plan = selectSupplier(mode, delayMs);
    Mono<String> source = Mono.fromCallable(plan.supplier()::get);
    if (plan.blocking()) {
        source = source.subscribeOn(Schedulers.boundedElastic());
    }

    return source
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .map(ApiResponse::success)
            .onErrorResume(throwable -> Mono.just(ApiResponse.failure(
                    ErrorResponse.from(throwable, circuitBreaker.getState().name())
            )));
}
```

**要点**：

1. **`Mono.fromCallable`** — 将同步调用包装为惰性 Mono，确保调用发生在订阅时
2. **`subscribeOn(Schedulers.boundedElastic())`** — 阻塞调用（如 slow 模式）必须切换到弹性线程池，避免阻塞 Netty 事件循环
3. **`transformDeferred`** — 每次订阅时检查熔断器状态，OPEN 状态直接抛出 `CallNotPermittedException`
4. **`onErrorResume`** — 捕获所有异常统一转为 `ApiResponse` 结构，避免异常传播到框架层

**背压处理**：`CircuitBreakerOperator` 透传背压信号，不引入额外缓冲。对于 `Flux` 场景，背压由上游 Publisher 和下游 Subscriber 协商，熔断器仅在订阅时做准入检查。

---

## 3. Fallback 降级策略

```java
private Mono<ApiResponse<String>> callWithCircuitBreaker(
        String name, String mode, long delayMs, boolean useFallback) {
    CircuitBreaker selected = resolveCircuitBreaker(name);
    CallPlan plan = selectSupplier(mode, delayMs);
    Mono<String> source = Mono.fromCallable(plan.supplier()::get);
    if (plan.blocking()) {
        source = source.subscribeOn(Schedulers.boundedElastic());
    }

    Mono<String> guarded = source.transformDeferred(CircuitBreakerOperator.of(selected));
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
                    ErrorResponse.from(throwable, selected.getState().name())
            )));
}
```

**降级触发条件**：
- 业务异常（如 `IllegalStateException`）— 外部调用本身失败
- `CallNotPermittedException` — 熔断器处于 OPEN 状态，调用被直接拒绝

**降级策略选型**：

| 策略 | 适用场景 | 示例 |
|------|---------|------|
| 静态默认值 | 数据不敏感，可接受默认 | `"fallback-{name}"` |
| 缓存数据 | 读多写少，容忍过期 | 从 Redis 取上次成功结果 |
| 备用服务 | 有冗余部署 | 切换到备用集群 |
| 空响应 | 非关键功能 | 返回空列表 |

本项目使用静态默认值方案，生产环境建议根据业务场景选择。

---

## 4. 组合熔断器（链式保护）

```java
return source
        .transformDeferred(CircuitBreakerOperator.of(primary))
        .transformDeferred(CircuitBreakerOperator.of(secondary))
        .map(ApiResponse::success)
        .onErrorResume(throwable -> Mono.just(ApiResponse.failure(
                toErrorResponse(throwable, primary, secondary)
        )));
```

**执行顺序**：

```
订阅方向（从外到内）：Subscriber → secondary CB → primary CB → Source
数据流向（从内到外）：Source → primary CB → secondary CB → Subscriber
```

- 订阅时 secondary 先检查，再检查 primary
- 如果 secondary 处于 OPEN，`CallNotPermittedException` 立即抛出，primary 和 source 都不会执行
- 如果 secondary 允许但 primary 处于 OPEN，primary 抛出异常
- 如果都允许，source 执行，结果依次经过 primary 和 secondary 的指标统计

**错误归因**：

```java
private ErrorResponse toErrorResponse(Throwable throwable,
        CircuitBreaker primary, CircuitBreaker secondary) {
    if (throwable instanceof CallNotPermittedException) {
        String state = primary.getState().name();
        if (secondary != null && !CircuitBreaker.State.OPEN.name().equals(state)) {
            state = secondary.getState().name();
        }
        return ErrorResponse.from(throwable, state);
    }
    return ErrorResponse.from(throwable, primary.getState().name());
}
```

`CallNotPermittedException` 发生时，通过检查 primary 状态判断异常来源：
- primary 为 OPEN → 异常来自 primary
- primary 非 OPEN → 异常来自 secondary

---

## 5. REST 接口

| 接口 | 说明 |
|------|------|
| `GET /api/circuit-breaker/test?mode=success&delayMs=0` | 基础调用（无降级） |
| `GET /api/circuit-breaker/test-fallback?mode=failure` | 带降级的调用 |
| `GET /api/circuit-breaker/test-reactor?name=externalApi&mode=success` | 指定熔断器名称调用 |
| `GET /api/circuit-breaker/test-combo?primary=a&secondary=b&mode=failure` | 组合熔断器调用 |
| `GET /api/circuit-breaker/state?name=externalApi` | 查询单个熔断器状态 |
| `GET /api/circuit-breaker/state-combo?primary=a&secondary=b` | 查询组合状态 |
| `GET /api/circuit-breaker/states` | 列出所有熔断器名称 |

**mode 参数**：`success`（默认）、`failure`（模拟异常）、`slow`（模拟慢调用）

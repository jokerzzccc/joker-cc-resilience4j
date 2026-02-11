# 降级模式指南

## 概述

当 CircuitBreaker 断开或 RateLimiter 拒绝请求时，降级策略决定如何响应客户端。本文档介绍多级降级模式的设计和实现。

---

## 降级层次

```
请求失败
    │
    ├── Level 1: 缓存降级 (Cache Fallback)
    │   返回最近一次成功的缓存数据
    │
    ├── Level 2: 静态降级 (Static Fallback)
    │   返回预定义的默认值
    │
    └── Level 3: 错误降级 (Error Fallback)
        返回错误信息给客户端
```

---

## 实现：ProductionService

```java
@Service
public class ProductionService {

    private final ConcurrentMap<String, String> fallbackCache = new ConcurrentHashMap<>();

    public Mono<ApiResponse<String>> callWithFullProtection(String mode, long delayMs) {
        return source
            .transformDeferred(RateLimiterOperator.of(rl))
            .transformDeferred(CircuitBreakerOperator.of(cb))
            .doOnNext(result -> fallbackCache.put("lastSuccess", result))  // 缓存成功结果
            .map(ApiResponse::success)
            .onErrorResume(this::multiLevelFallback);
    }

    private Mono<ApiResponse<String>> multiLevelFallback(Throwable throwable) {
        // Level 1: 缓存降级
        String cached = fallbackCache.get("lastSuccess");
        if (cached != null) {
            return Mono.just(ApiResponse.success("cached:" + cached));
        }

        // Level 2: 静态降级（仅针对 Resilience4j 异常）
        if (throwable instanceof CallNotPermittedException
                || throwable instanceof RequestNotPermitted) {
            return Mono.just(ApiResponse.success("static-fallback"));
        }

        // Level 3: 错误降级
        return Mono.just(ApiResponse.failure(
                ErrorResponse.from(throwable, "DEGRADED")));
    }
}
```

---

## 降级模式详解

### Level 1: 缓存降级

**原理**：每次成功调用时通过 `doOnNext` 缓存结果，失败时返回缓存数据。

**适用场景**：
- 数据变化不频繁（商品信息、用户配置）
- 短暂不一致可接受
- 有历史数据优于无数据

**注意事项**：
- 缓存可能过期，需要考虑 TTL
- 本项目使用 `ConcurrentHashMap`，生产环境建议使用 Redis/Caffeine
- 通过 `/api/production/cache` 端点可查看缓存状态
- 通过 `DELETE /api/production/cache` 端点可清除缓存

### Level 2: 静态降级

**原理**：当缓存为空且异常是 Resilience4j 类型时，返回预定义默认值。

**适用场景**：
- 首次调用就失败（无缓存数据）
- 非核心功能降级（如推荐列表返回空）
- 需要保证请求不报错

**注意事项**：
- 静态降级只处理 `CallNotPermittedException` 和 `RequestNotPermitted`
- 业务异常（如参数错误）不应使用静态降级

### Level 3: 错误降级

**原理**：无法通过缓存或静态值恢复时，返回错误信息。

**适用场景**：
- 业务逻辑异常
- 无法降级的核心操作
- 需要通知客户端重试

---

## 组合保护模式

RateLimiter 在前，CircuitBreaker 在后：

```java
source
    .transformDeferred(RateLimiterOperator.of(rl))   // 先限流：快速拒绝超量请求
    .transformDeferred(CircuitBreakerOperator.of(cb)) // 后熔断：保护下游服务
```

**执行顺序**：
1. RateLimiter 检查许可 → 超限直接拒绝（不消耗 CB 的窗口计数）
2. CircuitBreaker 检查状态 → OPEN 时直接拒绝
3. 执行实际调用
4. 结果反馈给 CircuitBreaker 更新指标

---

## 降级选择决策树

```
异常类型？
    │
    ├── CallNotPermittedException (CB Open)
    │   └── 有缓存？ → 返回缓存 : 返回静态值
    │
    ├── RequestNotPermitted (Rate Limited)
    │   └── 有缓存？ → 返回缓存 : 返回静态值
    │
    └── 其他异常 (业务异常)
        └── 有缓存？ → 返回缓存 : 返回错误信息
```

---

## 最佳实践

1. **缓存优先**：有缓存数据时优先使用缓存，提供最好的用户体验
2. **区分异常类型**：Resilience4j 异常可以安全降级，业务异常需要谨慎处理
3. **doOnNext 缓存**：利用 Reactor 的 `doOnNext` 操作符在成功时自动缓存
4. **缓存管理**：提供缓存查看和清除接口，便于运维排查
5. **日志记录**：每次降级都应记录日志，便于事后分析

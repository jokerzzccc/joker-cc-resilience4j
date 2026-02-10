# RateLimiter 高级模式

## 1. 工厂模式（RateLimiterFactory）

### 设计目标

在微服务场景下，一个应用可能需要对多个外部依赖设置不同的限流策略。直接在业务代码中 `RateLimiter.of(name, config)` 会导致：

- 实例管理分散，无法统一查询和监控
- 同名 RateLimiter 可能被重复创建
- 配置模板无法复用

`RateLimiterFactory` 解决这些问题：

```java
public class RateLimiterFactory {

    private final ConcurrentMap<String, RateLimiter> cache = new ConcurrentHashMap<>();
    private final EnumMap<ConfigTemplate, RateLimiterConfig> templates;
    private final Set<String> listenerAttached = ConcurrentHashMap.newKeySet();
}
```

**核心设计决策**：
- `ConcurrentHashMap` — 线程安全的实例缓存，支持并发创建
- `EnumMap` — 有限模板集合，类型安全且内存高效
- `listenerAttached` — 防止重复注册事件监听器

### 配置模板

| 模板 | 适用场景 | 关键参数 |
|------|---------|---------|
| `STRICT` | 保护脆弱依赖，严格控制 QPS | limitForPeriod=5, refreshPeriod=1s, timeout=ZERO |
| `LENIENT` | 高吞吐稳定接口，允许排队 | limitForPeriod=100, refreshPeriod=1s, timeout=500ms |
| `BURST` | 通用默认配置 | 使用构造时传入的 baseConfig |

**参数对比**：

```
STRICT:   低许可 + 立即拒绝 → 适合脆弱或昂贵的外部依赖
LENIENT:  高许可 + 允许排队 → 适合高可用的稳定服务
BURST:    使用 baseConfig → 适合大多数场景
```

### 使用方式

**模板创建**：

```java
RateLimiterFactory factory = new RateLimiterFactory(baseConfig);
RateLimiter strict = factory.create("payment-api", ConfigTemplate.STRICT);
RateLimiter lenient = factory.create("user-api", ConfigTemplate.LENIENT);
```

**自定义配置**：

```java
RateLimiter custom = factory.create("inventory-api", builder -> builder
        .limitForPeriod(20)
        .limitRefreshPeriod(Duration.ofSeconds(1))
        .timeoutDuration(Duration.ofMillis(200))
);
```

**单例保证**：
`computeIfAbsent` 保证相同名称只创建一次。后续调用直接返回缓存实例：

```java
public RateLimiter create(String name, RateLimiterConfig config) {
    RateLimiter rateLimiter = cache.computeIfAbsent(
            name, key -> RateLimiter.of(key, config));
    attachListenersIfNeeded(rateLimiter);
    return rateLimiter;
}
```

### 动态更新

```java
RateLimiter updated = factory.update("payment-api", RateLimiterConfig.custom()
        .limitForPeriod(3)
        .limitRefreshPeriod(Duration.ofSeconds(1))
        .timeoutDuration(Duration.ZERO)
        .build());
```

`update` 会创建全新实例替换缓存，同时清除旧的 listener 标记以确保新实例正确注册事件监听。需要注意：已持有旧实例引用的调用方不会自动切换，适用于通过 Factory 解析名称的场景。

---

## 2. 事件监听与日志

```java
private void attachListenersIfNeeded(RateLimiter rateLimiter) {
    if (!listenerAttached.add(rateLimiter.getName())) {
        return; // 已注册，跳过
    }

    rateLimiter.getEventPublisher()
            .onSuccess(event -> log.info(
                    "rateLimiter={} permitted type={}",
                    event.getRateLimiterName(),
                    event.getEventType()
            ))
            .onFailure(event -> log.warn(
                    "rateLimiter={} rejected type={}",
                    event.getRateLimiterName(),
                    event.getEventType()
            ));
}
```

**事件类型与用途**：

| 事件 | 触发条件 | 日志级别 | 生产用途 |
|------|---------|---------|---------|
| `onSuccess` | 请求获得许可并完成 | INFO | 流量监控 |
| `onFailure` | 请求被限流拒绝 | WARN | 触发告警 |

与 CircuitBreaker 的区别：CircuitBreaker 有 4 种事件（stateTransition、failureRateExceeded、slowCallRateExceeded、callNotPermitted），RateLimiter 只有 2 种（onSuccess、onFailure），因为限流器没有状态转换的概念。

**防重复注册**：`listenerAttached.add()` 利用 `ConcurrentHashMap.newKeySet()` 的原子性，确保每个限流器名称只注册一次监听器。

---

## 3. 状态查询接口

```
GET /api/rate-limiter/state?name=externalApi
GET /api/rate-limiter/states
GET /api/rate-limiter/state-combo?primary=externalApi&secondary=secondaryApi
```

**状态响应结构**：

```json
{
    "success": true,
    "data": {
        "name": "externalApi",
        "availablePermissions": 8,
        "numberOfWaitingThreads": 0
    },
    "error": null
}
```

`/states` 返回 Factory 管理的所有限流器名称（排序），用于运维巡检。

---

## 4. 组合限流与多维度保护

### 场景

一次业务请求需要同时满足多个限流维度：

```
用户请求 → [全局限流 100 QPS] → [接口限流 10 QPS] → 业务处理
```

### 实践建议

1. **独立限流器**：每个限流维度使用独立的 RateLimiter 实例
2. **差异化配置**：全局限流用 LENIENT，接口级限流用 STRICT
3. **统一管理**：通过 `RateLimiterFactory` 集中创建和查询
4. **命名规范**：建议使用 `{维度}-{服务名}` 格式，如 `global-gateway`、`api-createOrder`

### Spring Bean 集成

```java
@Configuration
public class CustomRateLimiterConfig {

    @Bean
    public RateLimiterFactory rateLimiterFactory(RateLimiterConfig config) {
        return new RateLimiterFactory(config);
    }

    @Bean
    public RateLimiter rateLimiter(RateLimiterFactory factory) {
        return factory.create("externalApi", ConfigTemplate.BURST);
    }
}
```

Factory 本身不是 Spring 管理的组件（无 `@Component`），而是作为 Bean 注入 Spring 容器。这样既保持了 Factory 的纯 Java 可测试性，又能享受 Spring 的生命周期管理。

# CircuitBreaker 高级模式

## 1. 工厂模式（CircuitBreakerFactory）

### 设计目标

在微服务场景下，一个应用可能调用多个外部依赖，每个依赖需要独立的熔断器和配置。直接在业务代码中 `CircuitBreaker.of(name, config)` 会导致：

- 实例管理分散，无法统一查询和监控
- 同名 CircuitBreaker 可能被重复创建
- 配置模板无法复用

`CircuitBreakerFactory` 解决这些问题：

```java
public class CircuitBreakerFactory {

    private final ConcurrentMap<String, CircuitBreaker> cache = new ConcurrentHashMap<>();
    private final EnumMap<ConfigTemplate, CircuitBreakerConfig> templates;
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
| `FAST_FAIL` | 快速检测故障，降低响应延迟 | failureRate=25%, window=4, minCalls=2, wait=2s |
| `SLOW_CALL` | 检测上游超时/变慢 | slowCallRate=50%, slowThreshold=400ms, window=10, minCalls=5 |
| `HYBRID` | 同时监控失败率和慢调用率 | 使用构造时传入的 baseConfig |

**参数对比**：

```
FAST_FAIL:  小窗口 + 低阈值 → 快速熔断，适合非关键依赖
SLOW_CALL:  关注延迟而非失败 → 适合对 RT 敏感的场景
HYBRID:     综合考虑 → 适合核心依赖
```

### 使用方式

**模板创建**：

```java
CircuitBreakerFactory factory = new CircuitBreakerFactory(baseConfig);
CircuitBreaker fast = factory.create("payment-api", ConfigTemplate.FAST_FAIL);
CircuitBreaker slow = factory.create("report-api", ConfigTemplate.SLOW_CALL);
```

**自定义配置**：

```java
CircuitBreaker custom = factory.create("inventory-api", builder -> builder
        .failureRateThreshold(30.0f)
        .slidingWindowSize(20)
        .minimumNumberOfCalls(10)
        .waitDurationInOpenState(Duration.ofSeconds(15))
);
```

**单例保证**：
`computeIfAbsent` 保证相同名称只创建一次。后续调用直接返回缓存实例：

```java
public CircuitBreaker create(String name, CircuitBreakerConfig config) {
    CircuitBreaker circuitBreaker = cache.computeIfAbsent(
            name, key -> CircuitBreaker.of(key, config));
    attachListenersIfNeeded(circuitBreaker);
    return circuitBreaker;
}
```

### 动态更新

```java
CircuitBreaker updated = factory.update("payment-api", CircuitBreakerConfig.custom()
        .failureRateThreshold(20.0f)
        .slidingWindowSize(4)
        .minimumNumberOfCalls(2)
        .build());
```

`update` 会创建全新实例替换缓存，同时清除旧的 listener 标记以确保新实例正确注册事件监听。需要注意：已持有旧实例引用的调用方不会自动切换，适用于通过 Factory 解析名称的场景。

---

## 2. 事件监听与日志

```java
private void attachListenersIfNeeded(CircuitBreaker circuitBreaker) {
    if (!listenerAttached.add(circuitBreaker.getName())) {
        return; // 已注册，跳过
    }

    circuitBreaker.getEventPublisher()
            .onStateTransition(event -> log.info(
                    "circuitBreaker={} transition {} -> {}",
                    event.getCircuitBreakerName(),
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()
            ))
            .onFailureRateExceeded(event -> log.warn(
                    "circuitBreaker={} failureRateExceeded={}%",
                    event.getCircuitBreakerName(),
                    event.getFailureRate()
            ))
            .onSlowCallRateExceeded(event -> log.warn(
                    "circuitBreaker={} slowCallRateExceeded={}%",
                    event.getCircuitBreakerName(),
                    event.getSlowCallRate()
            ))
            .onCallNotPermitted(event -> log.warn(
                    "circuitBreaker={} callNotPermitted",
                    event.getCircuitBreakerName()
            ));
}
```

**事件类型与用途**：

| 事件 | 触发条件 | 日志级别 | 生产用途 |
|------|---------|---------|---------|
| `onStateTransition` | 状态变更（如 CLOSED→OPEN） | INFO | 告警通知 |
| `onFailureRateExceeded` | 失败率超过阈值 | WARN | 触发告警 |
| `onSlowCallRateExceeded` | 慢调用率超过阈值 | WARN | 性能监控 |
| `onCallNotPermitted` | 调用被熔断器拒绝 | WARN | 流量监控 |

**防重复注册**：`listenerAttached.add()` 利用 `ConcurrentHashMap.newKeySet()` 的原子性，确保每个熔断器名称只注册一次监听器。

---

## 3. 状态查询接口

```
GET /api/circuit-breaker/state?name=externalApi
GET /api/circuit-breaker/states
GET /api/circuit-breaker/state-combo?primary=externalApi&secondary=secondaryApi
```

**状态响应结构**：

```json
{
    "success": true,
    "data": {
        "name": "externalApi",
        "state": "CLOSED",
        "failureRate": 0.0,
        "slowCallRate": 0.0,
        "bufferedCalls": 5,
        "failedCalls": 1,
        "slowCalls": 0,
        "notPermittedCalls": 0
    },
    "error": null
}
```

`/states` 返回 Factory 管理的所有熔断器名称（排序），用于运维巡检。

---

## 4. 组合熔断与多依赖保护

### 场景

一次业务请求依赖多个外部服务时，为每个依赖配置独立熔断器：

```
用户请求 → [订单服务 CB] → [库存服务 CB] → [支付服务 CB]
```

### 实践建议

1. **独立熔断器**：每个外部依赖使用独立的 CircuitBreaker 实例，避免一个依赖故障影响其他依赖的熔断判断
2. **差异化配置**：核心依赖（支付）使用 HYBRID 模板，非核心依赖（日志上报）使用 FAST_FAIL
3. **统一管理**：通过 `CircuitBreakerFactory` 集中创建和查询，便于监控和运维
4. **命名规范**：建议使用 `{服务名}-{接口}` 格式，如 `payment-createOrder`、`inventory-query`

### Spring Bean 集成

```java
@Configuration
public class CustomCircuitBreakerConfig {

    @Bean
    public CircuitBreakerFactory circuitBreakerFactory(CircuitBreakerConfig config) {
        return new CircuitBreakerFactory(config);
    }

    @Bean
    public CircuitBreaker circuitBreaker(CircuitBreakerFactory factory) {
        return factory.create("externalApi", ConfigTemplate.HYBRID);
    }
}
```

Factory 本身不是 Spring 管理的组件（无 `@Component`），而是作为 Bean 注入 Spring 容器。这样既保持了 Factory 的纯 Java 可测试性，又能享受 Spring 的生命周期管理。

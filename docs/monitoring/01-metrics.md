# 指标收集

## 1. Micrometer 核心概念

Micrometer 是 Java 指标收集的门面库（类比 SLF4J 之于日志），提供统一 API 对接各种监控系统。

| 概念 | 说明 | 典型用途 |
|------|------|---------|
| `MeterRegistry` | 指标注册中心 | Spring Boot 自动配置，无需手动创建 |
| `Counter` | 单调递增计数器 | 请求总数、错误总数 |
| `Timer` | 计时器（含计数） | 接口响应时间、调用耗时 |
| `Gauge` | 瞬时值指标 | 当前连接数、可用许可数 |

### 三种指标类型的数据采集方式对比

| 类型 | 数据写入方 | 数据读取时机 | 典型代码 |
|------|-----------|-------------|---------|
| **Counter** | 业务代码主动调用 `increment()` | Prometheus scrape 时读取累计值 | `counter.increment()` |
| **Timer** | 业务代码主动调用 `record()` | Prometheus scrape 时读取统计值 | `timer.record(duration)` |
| **Gauge** | 无需主动写入 | Prometheus scrape 时执行注册的 lambda 函数实时读取 | `Gauge.builder(name, obj, lambda)` |

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

### 生效时机

1. **Spring 容器启动** → `MetricsConfig` 创建 `MeterBinder` Bean
2. **Spring Boot 自动配置** → `MeterRegistryPostProcessor` 检测所有 `MeterBinder` Bean，调用 `bindTo(meterRegistry)`
3. **`TaggedCircuitBreakerMetrics.bindTo()`** → 向 Registry 注册 **EventConsumer**（事件监听器）
4. 此后 Registry 内任何 CircuitBreaker 实例的 **每一次调用**（成功/失败/不被允许）都会触发事件，自动更新对应的 Micrometer 指标

### 生效原理：事件驱动（推送式）

```
业务调用 → CircuitBreaker.executeSupplier() / transformDeferred()
         → 调用成功或失败
         → CircuitBreaker 发布 CircuitBreakerOnSuccessEvent / CircuitBreakerOnErrorEvent
         → TaggedCircuitBreakerMetrics 内部的 EventConsumer 接收事件
         → 更新 Micrometer Counter/Timer（calls_seconds_count、calls_seconds_sum 等）
         → Prometheus scrape 时读取这些已更新的值
```

关键点：**业务代码无需感知指标的存在**。只要调用通过 CircuitBreaker/RateLimiter 执行，指标就会自动采集。

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

### 三类指标的生效机制详解

#### 第一类：Tagged*Metrics（自动生效，事件驱动）

```java
TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry);
```

- **生效时机**：Spring 容器启动后立即生效
- **数据绑定**：通过 `CircuitBreakerRegistry` 的 `EventConsumer` 监听所有实例的调用事件
- **业务代码是否需要感知**：不需要。只要调用通过 `transformDeferred(CircuitBreakerOperator.of(...))` 执行，指标自动更新
- **采集的指标**：`resilience4j_circuitbreaker_calls_seconds_count/sum`（按 kind=successful/failed/ignored 分 tag）
- **局限性**：只能监控 `CircuitBreakerRegistry` 内的实例。本项目的 `CircuitBreakerFactory` 绕过了 Registry（内部用 `ConcurrentHashMap` 管理），所以 Factory 创建的实例不会被 Tagged*Metrics 监控

#### 第二类：ResilienceMetricsRegistrar 的 Gauge（自动生效，拉取式）

```java
Gauge.builder("resilience4j.circuitbreaker.state", cb,
        c -> c.getState().getOrder())
    .register(meterRegistry);
```

- **生效时机**：`ResilienceMetricsRegistrar` 构造时立即注册，之后每次 Prometheus scrape 时执行 lambda
- **数据绑定**：注册时传入 CircuitBreaker/RateLimiter **对象引用**和 **lambda 函数**。Gauge 持有对象的强引用，每次读取时通过 lambda 调用对象的 getter 获取最新值
- **业务代码是否需要感知**：不需要。Gauge 直接读取 Resilience4j 实例内部的 Metrics 对象
- **数据流**：

```
Prometheus scrape /actuator/prometheus
  → MeterRegistry 遍历所有已注册的 Meter
  → 遇到 Gauge 时执行注册的 lambda
  → lambda 调用 cb.getState().getOrder() / cb.getMetrics().getFailureRate() 等
  → 返回当前瞬时值
  → 格式化为 Prometheus 文本输出
```

- **设计目的**：弥补 Tagged*Metrics 无法覆盖 Factory 创建的实例的缺口

#### 第三类：自定义 Counter/Timer（需手动调用，当前未生效）

```java
Counter.builder("resilience4j.custom.circuitbreaker.calls.total").register(meterRegistry);
Timer.builder("resilience4j.custom.api.call.duration").register(meterRegistry);
```

- **生效时机**：Bean 创建后指标即注册到 MeterRegistry，但 **值始终为 0**，直到业务代码主动调用 `increment()` / `record()`
- **数据绑定**：**无自动绑定**。Counter 和 Timer 是被动型指标，必须由业务代码主动写入
- **当前状态**：项目中 `CircuitBreakerService`、`RateLimiterService` 等业务服务**没有注入也没有调用**这些 Counter/Timer，因此它们始终为 0
- **MetricsController 的作用**：`MetricsController.snapshot()` 仅**读取**这些指标的当前值（`.count()`），不会让它们增长

**如果要让其生效**，需要在 Service 层注入并调用：

```java
@Service
public class CircuitBreakerService {
    private final Counter circuitBreakerCallCounter;
    private final Timer apiCallTimer;

    public CircuitBreakerService(Counter circuitBreakerCallCounter, Timer apiCallTimer, ...) {
        this.circuitBreakerCallCounter = circuitBreakerCallCounter;
        this.apiCallTimer = apiCallTimer;
    }

    public Mono<ApiResponse<String>> callExternal(String mode, long delayMs) {
        circuitBreakerCallCounter.increment();  // 每次调用 +1
        return Mono.deferContextual(ctx -> {
            long start = System.nanoTime();
            return callWithCircuitBreaker(circuitBreaker.getName(), mode, delayMs, false)
                .doFinally(signal ->
                    apiCallTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS)
                );
        });
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

## 7. 完整数据流：从业务调用到 Prometheus 输出

```
用户请求 → Controller → Service.callExternal()
  → Mono.fromCallable(externalApiService::call)
      .transformDeferred(CircuitBreakerOperator.of(cb))
  → CircuitBreaker 内部记录调用结果到滑动窗口
  → CircuitBreaker 发布事件（OnSuccess/OnError/OnStateTransition）
  → TaggedCircuitBreakerMetrics 的 EventConsumer 接收事件 → 更新 Counter/Timer
  → cb.getMetrics() 内部统计值同步更新（failureRate, slowCallRate 等）

Prometheus 定时 scrape → GET /actuator/prometheus
  → PrometheusMeterRegistry 遍历所有 Meter
  → Tagged*Metrics 注册的 Counter/Timer → 直接读取累计值
  → ResilienceMetricsRegistrar 注册的 Gauge → 执行 lambda 读取 cb.getMetrics() 最新值
  → 自定义 Counter/Timer → 读取累计值（当前为 0，因为没有代码调用 increment/record）
  → 格式化为 Prometheus 文本格式返回
```

---

## 8. 最佳实践

- **命名规范**：使用 `.` 分隔的层级名称（如 `resilience4j.circuitbreaker.state`），Prometheus 会自动转换为 `_`
- **Tag 策略**：用 `name` tag 区分不同实例，避免为每个实例创建独立指标名
- **基数控制**：Tag 值应有限且可枚举，避免用户 ID 等高基数值作为 tag
- **双层绑定**：Registry 级别（自动发现新实例）+ 实例级别（精确控制已知实例）
- **优先使用 Gauge**：对于 Resilience4j 内部已维护的统计值（failureRate、state 等），用 Gauge + lambda 拉取比 Counter 推送更简单可靠
- **自定义 Counter/Timer 需配套业务埋点**：注册到 MeterRegistry 只是"声明"指标，必须在业务代码中调用 `increment()` / `record()` 才会有数据

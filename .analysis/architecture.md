# Resilience4j 学习项目技术架构

## 架构概述

本项目采用**纯 Reactor 响应式架构**，核心基于 **resilience4j-reactor** 模块，通过编程式 API 集成 Resilience4j 2.3 的 CircuitBreaker 和 RateLimiter，使用 Project Reactor 作为响应式编程框架，结合 Spring Boot 3.3 提供 Web 接口和监控能力。

### 核心设计理念

- **编程式 API 优先**：不使用 Spring AOP 注解（如 `@CircuitBreaker`、`@RateLimiter`），而是通过 Reactor 操作符直接集成
- **Reactor 原生集成**：使用 `transformDeferred` 操作符实现无缝集成
- **手动配置管理**：通过代码配置 Registry 和实例，而非完全依赖 Spring Boot 自动配置
- **响应式全链路**：从 Controller 到 Service 全部使用 Mono/Flux 响应式类型

---

## 技术栈

### 核心技术

| 技术 | 版本 | 用途 |
|------|------|------|
| JDK | 21 (LTS) | 运行时环境，支持虚拟线程等新特性 |
| Spring Boot | 3.3.0 | 应用框架（仅用于基础设施） |
| Spring WebFlux | 6.1.x | 响应式 Web 框架 |
| Project Reactor | 3.6.x | 响应式编程库（核心） |
| **resilience4j-reactor** | **2.3.0** | **Reactor 集成模块（核心）** |
| resilience4j-circuitbreaker | 2.3.0 | 熔断器核心库 |
| resilience4j-ratelimiter | 2.3.0 | 限流器核心库 |

**重要说明**：
- 本项目**不使用** `resilience4j-spring-boot3` 的自动配置和注解
- 本项目**不使用** `@CircuitBreaker`、`@RateLimiter` 等 AOP 注解
- 所有集成通过 **resilience4j-reactor** 的编程式 API 实现
- 使用 `CircuitBreakerOperator.of()` 和 `RateLimiterOperator.of()` 操作符

### 监控和指标

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot Actuator | 3.3.0 | 应用监控和管理 |
| Micrometer | 1.13.x | 指标收集抽象层 |
| Prometheus | - | 指标存储和查询 |
| Grafana | - | 指标可视化 |

### 构建和测试

| 技术 | 版本 | 用途 |
|------|------|------|
| Maven | 3.9+ | 构建工具 |
| JUnit 5 | 5.10.x | 单元测试框架 |
| Reactor Test | 3.6.x | 响应式测试工具 |
| Lombok | 1.18.x | 减少样板代码 |

---

## 系统架构

### 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        Client Layer                          │
│                    (HTTP/REST Clients)                       │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     Controller Layer                         │
│  ┌──────────────────────┐  ┌──────────────────────┐        │
│  │ CircuitBreaker       │  │ RateLimiter          │        │
│  │ Controller           │  │ Controller           │        │
│  └──────────────────────┘  └──────────────────────┘        │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      Service Layer                           │
│  ┌──────────────────────┐  ┌──────────────────────┐        │
│  │ CircuitBreaker       │  │ RateLimiter          │        │
│  │ Service              │  │ Service              │        │
│  └──────────────────────┘  └──────────────────────┘        │
│                              │                               │
│  ┌──────────────────────────────────────────────┐          │
│  │         ExternalApiService                    │          │
│  │      (模拟外部服务调用)                        │          │
│  └──────────────────────────────────────────────┘          │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Resilience4j Layer                         │
│  ┌──────────────────────┐  ┌──────────────────────┐        │
│  │ CircuitBreaker       │  │ RateLimiter          │        │
│  │ Registry             │  │ Registry             │        │
│  └──────────────────────┘  └──────────────────────┘        │
│                              │                               │
│  ┌──────────────────────────────────────────────┐          │
│  │         Reactor Operators                     │          │
│  │    (transformDeferred)                        │          │
│  └──────────────────────────────────────────────┘          │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Monitoring Layer                          │
│  ┌──────────────────────┐  ┌──────────────────────┐        │
│  │ Micrometer           │  │ Actuator             │        │
│  │ Metrics              │  │ Endpoints            │        │
│  └──────────────────────┘  └──────────────────────┘        │
│                              │                               │
│  ┌──────────────────────────────────────────────┐          │
│  │         Prometheus Exporter                   │          │
│  └──────────────────────────────────────────────┘          │
└─────────────────────────────────────────────────────────────┘
```

---

## 模块设计

### 1. Controller 层

**职责**：
- 接收 HTTP 请求
- 参数验证
- 调用 Service 层
- 返回响应式结果（Mono/Flux）

**关键类**：
- `CircuitBreakerController`: 熔断器示例接口
- `RateLimiterController`: 限流器示例接口

### 2. Service 层

**职责**：
- 业务逻辑处理
- 集成 Resilience4j 保护机制
- 实现降级策略
- 错误处理

**关键类**：
- `CircuitBreakerService`: 熔断器业务逻辑
- `RateLimiterService`: 限流器业务逻辑
- `ExternalApiService`: 模拟外部服务调用

### 3. Config 层

**职责**：
- Resilience4j 配置
- 监控配置
- 自定义配置
- **工厂模式实现**

**关键类**：
- `CircuitBreakerConfig`: 熔断器配置
- `RateLimiterConfig`: 限流器配置
- `MetricsConfig`: 监控指标配置
- **`CircuitBreakerFactory`: 熔断器工厂类（阶段三）**
- **`RateLimiterFactory`: 限流器工厂类（阶段五）**

### 4. Model 层

**职责**：
- 数据传输对象（DTO）
- 响应模型
- 异常模型

**关键类**：
- `ApiResponse`: 统一响应模型
- `ErrorResponse`: 错误响应模型

---

## Resilience4j 集成架构

### 工厂模式设计（阶段三和阶段五）

#### CircuitBreakerFactory 设计

```java
/**
 * CircuitBreaker 工厂类
 * 职责：
 * 1. 统一管理 CircuitBreaker 实例创建
 * 2. 提供预定义配置模板
 * 3. 支持自定义配置
 * 4. 实现单例管理，避免重复创建
 */
public class CircuitBreakerFactory {

    // 预定义配置模板
    public enum ConfigTemplate {
        FAST_FAIL,      // 快速失败模式
        SLOW_CALL,      // 慢调用检测模式
        HYBRID          // 混合模式
    }

    // 根据名称和模板创建
    public CircuitBreaker create(String name, ConfigTemplate template);

    // 根据自定义配置创建
    public CircuitBreaker create(String name, CircuitBreakerConfig config);

    // 获取已存在的实例
    public Optional<CircuitBreaker> get(String name);
}
```

#### RateLimiterFactory 设计

```java
/**
 * RateLimiter 工厂类
 * 职责：
 * 1. 统一管理 RateLimiter 实例创建
 * 2. 提供预定义配置模板
 * 3. 支持自定义配置
 * 4. 实现单例管理，避免重复创建
 */
public class RateLimiterFactory {

    // 预定义配置模板
    public enum ConfigTemplate {
        STRICT,         // 严格限流模式
        LENIENT,        // 宽松限流模式
        BURST           // 突发流量模式
    }

    // 根据名称和模板创建
    public RateLimiter create(String name, ConfigTemplate template);

    // 根据自定义配置创建
    public RateLimiter create(String name, RateLimiterConfig config);

    // 获取已存在的实例
    public Optional<RateLimiter> get(String name);
}
```

#### 工厂模式优势

1. **统一管理**: 集中管理所有实例，避免重复创建
2. **配置复用**: 预定义模板减少重复配置代码
3. **易于测试**: 便于 Mock 和单元测试
4. **扩展性强**: 易于添加新的配置模板
5. **类型安全**: 编译时检查，避免运行时错误

### CircuitBreaker 架构

```
┌─────────────────────────────────────────────────────────────┐
│                    CircuitBreaker Flow                       │
└─────────────────────────────────────────────────────────────┘

Request
   │
   ▼
┌──────────────────┐
│ Check CB State   │
└──────────────────┘
   │
   ├─── CLOSED ────────────────────────────────────┐
   │                                                │
   ├─── OPEN ──────────────────────────────────┐   │
   │                                            │   │
   └─── HALF_OPEN ─────────────────────────┐   │   │
                                            │   │   │
                                            ▼   ▼   ▼
                                    ┌──────────────────────┐
                                    │  Execute Request     │
                                    └──────────────────────┘
                                            │
                                            ├─── Success ───┐
                                            │                │
                                            └─── Failure ───┤
                                                             │
                                                             ▼
                                                    ┌─────────────────┐
                                                    │ Update Metrics  │
                                                    └─────────────────┘
                                                             │
                                                             ▼
                                                    ┌─────────────────┐
                                                    │ State Transition│
                                                    └─────────────────┘
```

**状态转换规则**：
- **CLOSED → OPEN**: 失败率或慢调用率超过阈值
- **OPEN → HALF_OPEN**: 等待时间到达后
- **HALF_OPEN → CLOSED**: 测试调用成功率达标
- **HALF_OPEN → OPEN**: 测试调用失败率过高

### RateLimiter 架构

```
┌─────────────────────────────────────────────────────────────┐
│                     RateLimiter Flow                         │
└─────────────────────────────────────────────────────────────┘

Request
   │
   ▼
┌──────────────────┐
│ Request Permission│
└──────────────────┘
   │
   ├─── Permission Granted ────────────────────────┐
   │                                                │
   └─── Permission Denied ─────────────────────┐   │
                                                │   │
                                                ▼   ▼
                                        ┌──────────────────┐
                                        │ Execute/Reject   │
                                        └──────────────────┘
                                                │
                                                ▼
                                        ┌──────────────────┐
                                        │ Update Metrics   │
                                        └──────────────────┘
                                                │
                                                ▼
                                        ┌──────────────────┐
                                        │ Refresh Permits  │
                                        │ (Time Window)    │
                                        └──────────────────┘
```

**限流策略**：
- **AtomicRateLimiter**: 基于原子操作的实现
- **SemaphoreBasedRateLimiter**: 基于信号量的实现

---

## Reactor 集成模式

### 使用 transformDeferred 操作符

```java
// CircuitBreaker 集成
public Mono<Response> callWithCircuitBreaker() {
    return externalService.call()
        .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
        .onErrorResume(this::handleError);
}

// RateLimiter 集成
public Mono<Response> callWithRateLimiter() {
    return externalService.call()
        .transformDeferred(RateLimiterOperator.of(rateLimiter))
        .onErrorResume(this::handleError);
}

// 组合使用
public Mono<Response> callWithBoth() {
    return externalService.call()
        .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
        .transformDeferred(RateLimiterOperator.of(rateLimiter))
        .onErrorResume(this::handleError);
}
```

### 错误处理策略

```
Request
   │
   ▼
┌──────────────────┐
│ Resilience4j     │
│ Protection       │
└──────────────────┘
   │
   ├─── Success ──────────────────────────────────┐
   │                                               │
   └─── Error ────────────────────────────────┐   │
                                               │   │
                                               ▼   ▼
                                        ┌──────────────────┐
                                        │ Error Handler    │
                                        └──────────────────┘
                                               │
                                               ├─── Fallback
                                               │
                                               ├─── Retry
                                               │
                                               └─── Propagate
```

---

## 监控架构

### 指标收集流程

```
┌─────────────────────────────────────────────────────────────┐
│                    Metrics Collection                        │
└─────────────────────────────────────────────────────────────┘

Resilience4j Events
   │
   ▼
┌──────────────────┐
│ Event Listeners  │
└──────────────────┘
   │
   ▼
┌──────────────────┐
│ Micrometer       │
│ Metrics Registry │
└──────────────────┘
   │
   ▼
┌──────────────────┐
│ Prometheus       │
│ Exporter         │
└──────────────────┘
   │
   ▼
┌──────────────────┐
│ Prometheus       │
│ Server           │
└──────────────────┘
   │
   ▼
┌──────────────────┐
│ Grafana          │
│ Dashboard        │
└──────────────────┘
```

### 关键指标

**CircuitBreaker 指标**：
- `resilience4j.circuitbreaker.state`: 熔断器状态
- `resilience4j.circuitbreaker.calls`: 调用次数（成功/失败）
- `resilience4j.circuitbreaker.failure.rate`: 失败率
- `resilience4j.circuitbreaker.slow.call.rate`: 慢调用率

**RateLimiter 指标**：
- `resilience4j.ratelimiter.available.permissions`: 可用许可数
- `resilience4j.ratelimiter.waiting.threads`: 等待线程数
- `resilience4j.ratelimiter.calls`: 调用次数（成功/拒绝）

---

## 配置架构

### 配置层次

```
┌─────────────────────────────────────────────────────────────┐
│                    Configuration Hierarchy                   │
└─────────────────────────────────────────────────────────────┘

application.yml (默认配置)
   │
   ├─── application-dev.yml (开发环境)
   │
   ├─── application-test.yml (测试环境)
   │
   └─── application-prod.yml (生产环境)

                    │
                    ▼
            ┌──────────────────┐
            │ Spring Boot      │
            │ Configuration    │
            └──────────────────┘
                    │
                    ▼
            ┌──────────────────┐
            │ Resilience4j     │
            │ Registry         │
            └──────────────────┘
                    │
                    ▼
            ┌──────────────────┐
            │ Runtime          │
            │ Configuration    │
            └──────────────────┘
```

### 配置示例

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 100
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
    instances:
      backendA:
        baseConfig: default
        failureRateThreshold: 30
      backendB:
        baseConfig: default

  ratelimiter:
    configs:
      default:
        limitForPeriod: 100
        limitRefreshPeriod: 1s
        timeoutDuration: 0
    instances:
      backendA:
        baseConfig: default
        limitForPeriod: 50
```

---

## 部署架构

### 开发环境

```
┌─────────────────────────────────────────────────────────────┐
│                    Development Environment                   │
└─────────────────────────────────────────────────────────────┘

Developer Machine
   │
   ├─── Spring Boot Application (localhost:8080)
   │
   ├─── Prometheus (localhost:9090)
   │
   └─── Grafana (localhost:3000)
```

### 生产环境

```
┌─────────────────────────────────────────────────────────────┐
│                    Production Environment                    │
└─────────────────────────────────────────────────────────────┘

Load Balancer
   │
   ├─── Application Instance 1
   │    └─── Resilience4j + Metrics
   │
   ├─── Application Instance 2
   │    └─── Resilience4j + Metrics
   │
   └─── Application Instance N
        └─── Resilience4j + Metrics
                    │
                    ▼
            ┌──────────────────┐
            │ Prometheus       │
            │ (Centralized)    │
            └──────────────────┘
                    │
                    ▼
            ┌──────────────────┐
            │ Grafana          │
            │ (Centralized)    │
            └──────────────────┘
                    │
                    ▼
            ┌──────────────────┐
            │ Alert Manager    │
            └──────────────────┘
```

---

## 安全架构

### 安全考虑

1. **配置安全**
   - 敏感配置加密
   - 环境变量注入
   - 配置访问控制

2. **端点安全**
   - Actuator 端点认证
   - 指标端点访问控制
   - HTTPS 加密传输

3. **监控安全**
   - Prometheus 访问认证
   - Grafana 用户权限管理
   - 告警信息脱敏

---

## 性能优化

### 优化策略

1. **响应式编程**
   - 非阻塞 I/O
   - 背压处理
   - 资源高效利用

2. **Resilience4j 优化**
   - 合理配置滑动窗口大小
   - 优化限流器刷新周期
   - 减少不必要的指标收集

3. **JDK 21 特性**
   - 虚拟线程（Virtual Threads）
   - 记录模式（Record Patterns）
   - 字符串模板（String Templates）

---

## 扩展性设计

### 水平扩展

- 无状态应用设计
- 支持多实例部署
- 负载均衡支持

### 功能扩展

- 插件化配置
- 自定义事件监听器
- 自定义降级策略

---

## 技术决策记录

### ADR-001: 选择 Reactor 作为响应式框架
- **决策**: 使用 Project Reactor
- **理由**: Spring WebFlux 原生支持，生态完善
- **日期**: 2026-01-31

### ADR-002: 选择 Resilience4j 而非 Hystrix
- **决策**: 使用 Resilience4j 2.3
- **理由**: Hystrix 已停止维护，Resilience4j 更轻量且活跃
- **日期**: 2026-01-31

### ADR-003: 选择 Prometheus + Grafana 监控方案
- **决策**: 使用 Prometheus + Grafana
- **理由**: 开源、成熟、社区支持好
- **日期**: 2026-01-31

### ADR-004: 使用工厂模式创建 Resilience4j 实例
- **决策**: 在阶段三和阶段五使用工厂模式
- **理由**:
  - 统一管理实例创建，避免重复
  - 提供预定义配置模板，简化使用
  - 提高代码可维护性和可测试性
  - 符合面向对象设计原则
- **日期**: 2026-02-01

---

**文档版本**: v1.1
**最后更新**: 2026-02-01
**维护人**: Claude

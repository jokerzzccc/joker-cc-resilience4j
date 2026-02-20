# Resilience4j 2.3 Reactor 学习项目

> 深入学习 Resilience4j 2.3 基于 resilience4j-reactor 的编程式 API

## 项目简介

本项目专注于学习 **Resilience4j 2.3** 的 **resilience4j-reactor** 模块，通过编程式 API 实现 CircuitBreaker 和 RateLimiter 在响应式场景下的应用。

**目标用户**：5 年以上 Java 后台开发经验

**核心特点**：
- ✅ 使用 `resilience4j-reactor` 编程式 API
- ✅ 通过 `transformDeferred` 操作符集成
- ❌ 不使用 Spring Boot 注解方式
- ✅ 全链路响应式编程（Mono/Flux）

## 技术栈

| 技术 | 版本 |
|------|------|
| JDK | 21 |
| Spring Boot | 3.3.0 |
| Spring WebFlux | 6.1.x |
| Project Reactor | 3.6.x |
| **resilience4j-reactor** | **2.3.0** |
| resilience4j-circuitbreaker | 2.3.0 |
| resilience4j-ratelimiter | 2.3.0 |
| Micrometer + Prometheus | - |

## 快速开始

### 1. 构建项目

```bash
mvn clean install
```

### 2. 运行应用

```bash
mvn spring-boot:run
```

### 3. 测试接口

```bash
# CircuitBreaker 测试
curl http://localhost:8080/api/circuit-breaker/test
curl http://localhost:8080/api/circuit-breaker/test-fallback?mode=failure
curl http://localhost:8080/api/circuit-breaker/state

# RateLimiter 测试
curl http://localhost:8080/api/rate-limiter/test
curl http://localhost:8080/api/rate-limiter/test-fallback?mode=failure
curl http://localhost:8080/api/rate-limiter/state

# 生产模式（CB + RL 组合保护 + 多级降级）
curl http://localhost:8080/api/production/test
curl http://localhost:8080/api/production/cache

# 动态配置更新
curl -X POST "http://localhost:8080/api/config/circuit-breaker/externalApi?failureRateThreshold=30"
curl http://localhost:8080/api/config/circuit-breaker/externalApi

# 指标快照
curl http://localhost:8080/api/metrics/snapshot

# Actuator
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/prometheus
```

完整 API 参考见 [docs/api-reference.md](docs/api-reference.md)。

## 项目结构

```
.
├── .analysis/                          # 核心文档（必读）
│   ├── implementation_plan.md          # 8 阶段实施计划
│   ├── implementation_progress.md      # 实时进度跟踪
│   └── architecture.md                 # 技术架构设计
├── src/main/java/com/joker/resilience4j/
│   ├── config/                         # 编程式配置（Factory、Properties、Metrics）
│   ├── controller/                     # REST 接口（5 个 Controller）
│   ├── service/                        # 业务逻辑（CB/RL/Production Service）
│   └── model/                          # 数据模型（record）
├── src/test/java/com/joker/resilience4j/
│   ├── config/                         # 配置层测试
│   ├── controller/                     # Controller 层测试
│   ├── service/                        # Service 层测试
│   ├── model/                          # Model 层测试
│   └── examples/                       # 可运行示例代码
├── docs/                               # 教学文档（20 份）
│   ├── circuitbreaker/                 # 熔断器文档（4 份）
│   ├── ratelimiter/                    # 限流器文档（4 份）
│   ├── production/                     # 生产实践文档（5 份）
│   ├── monitoring/                     # 监控文档（4 份）
│   ├── api-reference.md               # REST API 参考
│   └── learning-summary.md            # 学习总结报告
└── pom.xml
```

## 核心 API 示例

### CircuitBreaker + Reactor

```java
// 编程式配置
CircuitBreakerConfig config = CircuitBreakerConfig.custom()
    .failureRateThreshold(50)
    .waitDurationInOpenState(Duration.ofSeconds(10))
    .slidingWindowSize(100)
    .build();

CircuitBreaker circuitBreaker = CircuitBreaker.of("backend", config);

// Reactor 集成
public Mono<Response> callWithCircuitBreaker() {
    return externalService.call()
        .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
        .onErrorResume(this::fallback);
}
```

### RateLimiter + Reactor

```java
// 编程式配置
RateLimiterConfig config = RateLimiterConfig.custom()
    .limitForPeriod(100)
    .limitRefreshPeriod(Duration.ofSeconds(1))
    .timeoutDuration(Duration.ZERO)
    .build();

RateLimiter rateLimiter = RateLimiter.of("backend", config);

// Reactor 集成
public Mono<Response> callWithRateLimiter() {
    return externalService.call()
        .transformDeferred(RateLimiterOperator.of(rateLimiter))
        .onErrorResume(this::handleRateLimit);
}
```

## 学习路径

本项目分为 8 个阶段，详见 [实施计划](.analysis/implementation_plan.md)：

1. **项目基础搭建** - Maven + Spring Boot + 基础结构
2. **CircuitBreaker 基础** - 三种状态、配置参数、测试
3. **CircuitBreaker + Reactor** - transformDeferred、降级、组合
4. **RateLimiter 基础** - 限流算法、配置参数、测试
5. **RateLimiter + Reactor** - transformDeferred、动态配置
6. **监控和指标** - Micrometer + Prometheus + Grafana
7. **生产实践** - 配置优化、错误处理、性能优化
8. **文档完善** - 代码审查、测试覆盖、学习总结

## 文档说明

### 核心文档（.analysis/）

| 文件 | 说明 |
|------|------|
| `implementation_plan.md` | 8 阶段执行蓝图 |
| `implementation_progress.md` | 实时进度跟踪器 |
| `architecture.md` | 技术架构设计 |

### 教学文档（docs/）

各阶段完成后生成的教学文档，面向 5 年以上 Java 开发者：

| 目录/文件 | 内容 | 生成阶段 |
|------|------|----------|
| `circuitbreaker/` | 熔断器基础、配置、Reactor 集成、高级模式 | 阶段 2-3 |
| `ratelimiter/` | 限流器基础、配置、Reactor 集成、高级模式 | 阶段 4-5 |
| `monitoring/` | 指标、Prometheus、Grafana、告警 | 阶段 6 |
| `production/` | 配置调优、错误处理、降级、性能、故障排查 | 阶段 7 |
| `api-reference.md` | 全部 REST 端点参考 | 阶段 8 |
| `learning-summary.md` | 学习总结报告 | 阶段 8 |

## 参考资料

- [Resilience4j 官方文档](https://resilience4j.readme.io/)
- [resilience4j-reactor 模块](https://resilience4j.readme.io/docs/getting-started-3#reactor)
- [Project Reactor 文档](https://projectreactor.io/docs/core/release/reference/)
- [Spring Boot 3.3 文档](https://docs.spring.io/spring-boot/docs/3.3.0/reference/html/)

## 许可证

本项目仅用于学习目的。

## 项目状态

- **完成度**: 100%（8/8 阶段完成）
- **测试**: 133 个测试全部通过
- **覆盖率**: JaCoCo LINE + BRANCH 100%
- **源文件**: 25 个 Java 源文件，31 个测试文件，8 个示例文件
- **文档**: 20 份教学文档

---

**版本**: v2.0
**最后更新**: 2026-02-20

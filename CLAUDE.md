# Resilience4j 2.3 Reactor 学习项目

## 项目目标

深入学习 **Resilience4j 2.3** 基于 **resilience4j-reactor** 的编程式 API，掌握 CircuitBreaker 和 RateLimiter 在响应式场景下的使用和生产实践。

**目标用户**：5 年以上 Java 后台开发经验

**核心特点**：
- ✅ 使用 `resilience4j-reactor` 编程式 API
- ✅ 通过 `transformDeferred` 操作符集成
- ❌ 不使用 Spring Boot 注解方式（`@CircuitBreaker`、`@RateLimiter`）
- ✅ 全链路响应式编程（Mono/Flux）

---

## ⚠️ 项目约束

1. **禁止执行 Docker 指令**：Docker 命令仅作为文档示例展示
2. **进度追踪**：每完成一个阶段，必须更新 `.analysis/implementation_progress.md`
3. **阶段审批**：逐阶段执行，每个阶段完成后需用户确认通过
4. **禁止捏造**：不清楚的内容必须先询问或查证
5. **先问后做**：使用 Ask 或 Plan 模式确认方案后再执行
6. **路径限制**：所有文件必须在项目路径内创建
7. **阶段前置**：执行每阶段前，必须先阅读 `.analysis/` 下所有文档
8. **自我检验**：每阶段完成后，先自检（准确性、格式、链接）再提交审核
9. **代码质量**：示例代码必须通过编译和测试
10. **提交流程**：用户确认通过 → git commit → 进入下一阶段
11. **防止幻觉**：每一阶段  用户确认之后，进入下一阶段之前，清空当前窗口的 context，再进入下一阶段。

---

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| JDK | 21 | 启用预览特性 |
| Spring Boot | 3.3.0 | 仅用于基础设施 |
| Spring WebFlux | 6.1.x | 响应式 Web |
| Project Reactor | 3.6.x | 响应式编程核心 |
| **resilience4j-reactor** | **2.3.0** | **核心依赖** |
| resilience4j-circuitbreaker | 2.3.0 | 熔断器 |
| resilience4j-ratelimiter | 2.3.0 | 限流器 |
| Micrometer + Prometheus | - | 监控指标 |

---

## 项目结构

```
.
├── .analysis/                          # 📋 核心文档（必读）
│   ├── implementation_plan.md          # 8 阶段实施计划
│   ├── implementation_progress.md      # 实时进度跟踪
│   └── architecture.md                 # 技术架构设计
├── src/main/java/com/joker/resilience4j/
│   ├── config/                         # 编程式配置
│   ├── controller/                     # REST 接口
│   ├── service/                        # 业务逻辑
│   └── model/                          # 数据模型
├── docs/                               # 📚 教学文档（各阶段生成）
│   ├── circuitbreaker/                 # 熔断器文档
│   ├── ratelimiter/                    # 限流器文档
│   ├── production/                     # 生产实践文档
│   ├── monitoring/                     # 监控文档
│   └── README.md                       # 文档规划说明
├── examples/                           # 示例代码
└── pom.xml
```

### 📋 .analysis 目录（⭐⭐⭐⭐⭐）

| 文件 | 职责 | 使用场景 |
|------|------|----------|
| `implementation_plan.md` | 8 阶段执行蓝图 | 开始新阶段前查看任务清单 |
| `implementation_progress.md` | 实时进度跟踪器 | 完成任务后更新状态，记录问题 |
| `architecture.md` | 技术架构设计 | 编码前理解架构，遇到问题时参考 |

### 📚 docs 目录（⭐⭐⭐⭐）

**教学文档**，各阶段完成后生成，面向 5 年以上 Java 开发者：

| 目录 | 内容 | 生成阶段 |
|------|------|----------|
| `circuitbreaker/` | 熔断器基础、配置、Reactor 集成、高级模式 | 阶段 2-3 |
| `ratelimiter/` | 限流器基础、配置、Reactor 集成、高级模式 | 阶段 4-5 |
| `production/` | 配置调优、错误处理、降级、性能、故障排查 | 阶段 7 |
| `monitoring/` | 指标、Prometheus、Grafana、告警 | 阶段 6 |

**文档特点**：
- 简洁明了，直击要点
- 代码优先，配合必要说明
- 实战导向，关注生产问题
- 完整可运行的代码示例

**使用流程**：
1. **启动时**：阅读 `architecture.md` → `implementation_plan.md` → `implementation_progress.md`
2. **开发中**：查看计划 → 执行任务 → 更新进度
3. **复盘时**：查看进度 → 总结笔记 → 评估标准

---

## 快速开始

```bash
# 1. 构建项目
mvn clean install

# 2. 运行应用
mvn spring-boot:run

# 3. 访问接口
curl http://localhost:8080/api/circuit-breaker/test
curl http://localhost:8080/api/rate-limiter/test

# 4. 查看监控
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/prometheus
```

---

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

---

## 学习路径

详见 `.analysis/implementation_plan.md`，分为 8 个阶段：

1. **项目基础搭建** - Maven + Spring Boot + 基础结构
2. **CircuitBreaker 基础** - 三种状态、配置参数、测试
3. **CircuitBreaker + Reactor** - transformDeferred、降级、组合
4. **RateLimiter 基础** - 限流算法、配置参数、测试
5. **RateLimiter + Reactor** - transformDeferred、动态配置
6. **监控和指标** - Micrometer + Prometheus + Grafana
7. **生产实践** - 配置优化、错误处理、性能优化
8. **文档完善** - 代码审查、测试覆盖、学习总结

---

## 参考资料

- [Resilience4j 官方文档](https://resilience4j.readme.io/)
- [resilience4j-reactor 模块](https://resilience4j.readme.io/docs/getting-started-3#reactor)
- [Project Reactor 文档](https://projectreactor.io/docs/core/release/reference/)
- [Spring Boot 3.3 文档](https://docs.spring.io/spring-boot/docs/3.3.0/reference/html/)

---

**版本**: v1.0
**最后更新**: 2026-01-31

# 文档规划说明

本目录存放各阶段的教学文档，面向 5 年以上 Java 后台开发经验的开发者。

## 文档结构

```
docs/
├── circuitbreaker/                     # 熔断器相关文档
│   ├── 01-basics.md                   # 基础概念和原理
│   ├── 02-configuration.md            # 配置详解
│   ├── 03-reactor-integration.md      # Reactor 集成
│   └── 04-advanced-patterns.md        # 高级模式
├── ratelimiter/                        # 限流器相关文档
│   ├── 01-basics.md                   # 基础概念和原理
│   ├── 02-configuration.md            # 配置详解
│   ├── 03-reactor-integration.md      # Reactor 集成
│   └── 04-advanced-patterns.md        # 高级模式
├── production/                         # 生产实践文档
│   ├── 01-configuration-tuning.md     # 配置调优
│   ├── 02-error-handling.md           # 错误处理策略
│   ├── 03-fallback-patterns.md        # 降级模式
│   ├── 04-performance.md              # 性能优化
│   └── 05-troubleshooting.md          # 故障排查
├── monitoring/                         # 监控相关文档
│   ├── 01-metrics.md                  # 指标收集
│   ├── 02-prometheus.md               # Prometheus 集成
│   ├── 03-grafana.md                  # Grafana 可视化
│   └── 04-alerting.md                 # 告警配置
└── README.md                           # 本文件
```

## 文档编写原则

### 1. 目标读者
- 5 年以上 Java 后台开发经验
- 熟悉 Spring Boot 和响应式编程基础
- 了解微服务架构和服务治理概念

### 2. 内容要求
- **简洁明了**：避免冗余，直击要点
- **代码优先**：用代码说话，配合必要的文字说明
- **实战导向**：关注生产环境实际问题
- **循序渐进**：从基础到高级，逐步深入

### 3. 文档格式

每个文档应包含：

```markdown
# 标题

## 概述
简要说明本文档的目标和内容（2-3 句话）

## 核心概念
关键概念的简洁说明（列表形式）

## 代码示例
完整可运行的代码示例，带注释

## 配置说明
配置参数详解（表格形式）

## 最佳实践
生产环境建议（列表形式）

## 常见问题
FAQ 形式的问题解答

## 参考资料
相关文档链接
```

### 4. 代码示例要求
- 必须是完整可运行的代码
- 关键部分添加注释
- 展示实际使用场景
- 包含错误处理

### 5. 避免的内容
- 过度理论化的解释
- 重复官方文档的内容
- 过于简单的 Hello World 示例
- 不切实际的示例代码

## 文档生成时机

| 阶段 | 生成文档 | 时机 |
|------|---------|------|
| 阶段一 | 无 | 项目基础搭建 |
| 阶段二 | `circuitbreaker/01-basics.md`<br>`circuitbreaker/02-configuration.md` | CircuitBreaker 基础实现完成后 |
| 阶段三 | `circuitbreaker/03-reactor-integration.md`<br>`circuitbreaker/04-advanced-patterns.md` | CircuitBreaker + Reactor 集成完成后 |
| 阶段四 | `ratelimiter/01-basics.md`<br>`ratelimiter/02-configuration.md` | RateLimiter 基础实现完成后 |
| 阶段五 | `ratelimiter/03-reactor-integration.md`<br>`ratelimiter/04-advanced-patterns.md` | RateLimiter + Reactor 集成完成后 |
| 阶段六 | `monitoring/01-metrics.md`<br>`monitoring/02-prometheus.md`<br>`monitoring/03-grafana.md`<br>`monitoring/04-alerting.md` | 监控体系建立完成后 |
| 阶段七 | `production/01-configuration-tuning.md`<br>`production/02-error-handling.md`<br>`production/03-fallback-patterns.md`<br>`production/04-performance.md`<br>`production/05-troubleshooting.md` | 生产实践完成后 |
| 阶段八 | 完善所有文档 | 项目收尾阶段 |

## 文档审核标准

每个文档完成后需要自检：

- [ ] 内容准确性：技术细节正确无误
- [ ] 代码可运行：所有示例代码经过验证
- [ ] 格式规范：遵循 Markdown 规范
- [ ] 链接有效：所有引用链接可访问
- [ ] 语言简洁：避免冗余和啰嗦
- [ ] 实用性强：解决实际问题

## 文档维护

- 代码变更时，同步更新相关文档
- 发现问题时，及时修正
- 新增最佳实践时，补充到对应文档

---

**创建日期**: 2026-01-31
**维护人**: Claude + User

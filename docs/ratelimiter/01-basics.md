# RateLimiter 基础

## 解决的问题
RateLimiter 用于保护系统免受过量请求的冲击。当流量超过系统承载能力时，限流器拒绝多余请求，确保核心服务稳定运行。

## 核心概念
- **Permit（许可）**：每次调用消耗一个许可，许可耗尽则拒绝请求。
- **Refresh Period（刷新周期）**：每个周期重置许可数量，实现滑动窗口限流。
- **Timeout（超时等待）**：请求无许可时的最大等待时间，超时则拒绝。

## 工作原理
```
请求到达 → 检查可用许可
  ├── 许可充足 → 消耗一个许可 → 执行请求
  └── 许可不足 → 等待（timeoutDuration）
        ├── 等待期间获得许可 → 执行请求
        └── 超时 → 抛出 RequestNotPermitted
```

## 两种实现
- **AtomicRateLimiter**（默认）：基于原子操作，无锁设计，性能更优。
- **SemaphoreBasedRateLimiter**：基于信号量，语义清晰，适合简单场景。

## 关键指标
- 可用许可数（available permissions）
- 等待线程数（waiting threads）

## 与 CircuitBreaker 的区别

| 维度 | CircuitBreaker | RateLimiter |
|------|---------------|-------------|
| 目标 | 防止故障扩散 | 控制请求速率 |
| 触发条件 | 失败率/慢调用率超阈值 | 许可耗尽 |
| 状态模型 | CLOSED/OPEN/HALF_OPEN | 无状态切换，仅管理许可 |
| 恢复方式 | 等待后试探恢复 | 周期自动刷新许可 |

## 本项目如何使用
本项目使用 Resilience4j 的编程式 API（非 Spring AOP 注解），
阶段四关注：
- 基础配置（limitForPeriod、limitRefreshPeriod、timeoutDuration）
- 手动创建 RateLimiter
- Reactor 集成：`transformDeferred(RateLimiterOperator.of(...))`
- 简单测试接口

## 示例
```
GET /api/rate-limiter/test?mode=success
GET /api/rate-limiter/test?mode=failure
GET /api/rate-limiter/test?mode=slow&delayMs=500
GET /api/rate-limiter/test-fallback?mode=failure
GET /api/rate-limiter/state
GET /api/rate-limiter/states
```

# RateLimiter 配置说明

## 配置目标
RateLimiter 配置应反映以下因素：
- 系统承载能力（TPS/QPS）
- 上游调用方的预期流量
- 业务对延迟的容忍度

## 本阶段使用的配置项
来自 `config/CustomRateLimiterConfig`：
- `limitForPeriod`：每个刷新周期的许可数量
- `limitRefreshPeriod`：许可刷新周期（每个周期重置许可）
- `timeoutDuration`：请求等待许可的最大超时时间

## 配置项详解

### limitForPeriod
每个刷新周期内允许的最大请求数。
- 默认值：50
- 本项目设置：10
- 计算方式：目标 QPS = limitForPeriod / limitRefreshPeriod（秒）

### limitRefreshPeriod
许可刷新的时间周期。每个周期结束后，可用许可重置为 limitForPeriod。
- 默认值：500ns（纳秒）
- 本项目设置：1s
- 建议：生产环境通常设为 1s，与 QPS 对齐

### timeoutDuration
请求等待许可的最大时间。超时后抛出 `RequestNotPermitted`。
- 默认值：5s
- 本项目设置：Duration.ZERO（不等待，立即拒绝）
- 建议：
  - `Duration.ZERO`：快速失败，适合高并发场景
  - 非零值：允许排队等待，适合突发流量场景

## 建议的调参方式
1. 根据系统压测结果确定安全 QPS，设置 `limitForPeriod`。
2. `limitRefreshPeriod` 通常设为 1 秒，便于理解和监控。
3. `timeoutDuration` 根据业务 SLA 决定是否允许等待。

## 项目默认配置示例
```
limitForPeriod: 10
limitRefreshPeriod: 1s
timeoutDuration: 0（不等待）
```

等效 QPS = 10 / 1s = 10 QPS

## 为什么使用编程式配置
本项目明确不使用 `resilience4j.*` 配置属性，
目的是专注于 Resilience4j 的编程式 API 与后续 Reactor 集成。

```java
RateLimiterConfig config = RateLimiterConfig.custom()
    .limitForPeriod(10)
    .limitRefreshPeriod(Duration.ofSeconds(1))
    .timeoutDuration(Duration.ZERO)
    .build();
```

# CircuitBreaker 配置说明

## 配置目标
CircuitBreaker 配置应反映以下因素：
- 依赖稳定性
- 可接受的延迟
- 恢复速度要求

## 本阶段使用的配置项
来自 `config/CircuitBreakerConfig`：
- `failureRateThreshold`：失败率阈值
- `slowCallRateThreshold`：慢调用率阈值
- `slowCallDurationThreshold`：慢调用时长阈值
- `slidingWindowSize`：滑动窗口大小
- `minimumNumberOfCalls`：最小统计调用数
- `permittedNumberOfCallsInHalfOpenState`：半开状态试探调用数
- `waitDurationInOpenState`：熔断后等待时间

## 建议的调参方式
1. 开发阶段用小窗口（例如 10 次调用）。
2. 生产阶段适度放大窗口，降低波动。
3. 慢调用阈值根据 SLA 或 P95 延迟确定。

## 项目默认配置示例
```
failureRateThreshold: 50%
slowCallRateThreshold: 50%
slowCallDurationThreshold: 2s
slidingWindowSize: 10
minimumNumberOfCalls: 5
permittedNumberOfCallsInHalfOpenState: 2
waitDurationInOpenState: 5s
```

## 为什么使用编程式配置
本项目明确不使用 `resilience4j.*` 配置属性，
目的是专注于 Resilience4j 的编程式 API 与后续 Reactor 集成。

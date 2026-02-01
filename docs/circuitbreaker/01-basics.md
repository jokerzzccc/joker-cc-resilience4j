# CircuitBreaker 基础

## 解决的问题
CircuitBreaker 用于防止故障连锁扩散。当依赖不稳定时，熔断器会短路请求，系统快速失败并给依赖恢复时间。

## 核心概念
- **Closed**：请求正常通过，统计失败和慢调用。
- **Open**：请求被立即拒绝，保护系统。
- **Half-Open**：允许少量试探请求，用于判断是否恢复。

## 状态转换
- **Closed → Open**：失败率或慢调用率超过阈值。
- **Open → Half-Open**：等待时间结束，开始放行试探请求。
- **Half-Open → Closed**：试探请求成功率达标。
- **Half-Open → Open**：试探请求失败或过慢。

## 关键指标
- 失败率（failure rate）
- 慢调用率（slow call rate）
- 缓冲调用数（buffered calls）
- 失败调用数（failed calls）
- 被拒绝调用数（not permitted calls）

## 本项目如何使用
本项目使用 Resilience4j 的编程式 API（非 Spring AOP 注解），
阶段二关注：
- 基础配置
- 手动创建 CircuitBreaker
- 简单测试接口

## 示例
```
GET /api/circuit-breaker/test?mode=success
GET /api/circuit-breaker/test?mode=failure
GET /api/circuit-breaker/test?mode=slow&delayMs=2500
GET /api/circuit-breaker/state
```

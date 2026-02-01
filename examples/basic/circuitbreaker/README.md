# CircuitBreaker 基础示例

该示例展示最小化的编程式 CircuitBreaker 用法（不依赖 Spring Boot 自动配置）。

运行应用后访问：
```
GET /api/circuit-breaker/test?mode=success
GET /api/circuit-breaker/test?mode=failure
GET /api/circuit-breaker/test?mode=slow&delayMs=2500
GET /api/circuit-breaker/state
```

当失败或慢调用达到阈值后，状态会从 CLOSED 进入 OPEN。

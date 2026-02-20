# API 参考文档

本文档汇总项目中所有 REST 端点，按 Controller 分组。

所有接口返回统一响应格式：

```json
{
  "success": true,
  "data": "...",
  "error": null
}
```

失败时：

```json
{
  "success": false,
  "data": null,
  "error": {
    "message": "错误信息",
    "type": "异常类型",
    "componentState": "组件状态"
  }
}
```

---

## 1. CircuitBreakerController

**基础路径**: `/api/circuit-breaker`

### GET /api/circuit-breaker/test

基础熔断器调用测试。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `mode` | String | `success` | 调用模式：`success` / `failure` / `slow` |
| `delayMs` | long | `0` | 慢调用延迟毫秒数（仅 `slow` 模式生效） |

```bash
curl "http://localhost:8080/api/circuit-breaker/test?mode=success"
curl "http://localhost:8080/api/circuit-breaker/test?mode=failure"
curl "http://localhost:8080/api/circuit-breaker/test?mode=slow&delayMs=3000"
```

### GET /api/circuit-breaker/test-fallback

带降级策略的熔断器调用，熔断时返回 `fallback-{name}` 而非错误。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `mode` | String | `success` | 调用模式 |
| `delayMs` | long | `0` | 慢调用延迟 |

```bash
curl "http://localhost:8080/api/circuit-breaker/test-fallback?mode=failure"
```

### GET /api/circuit-breaker/test-reactor

指定命名熔断器调用（通过 Factory 自动创建）。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | String | `externalApi` | 熔断器名称 |
| `mode` | String | `success` | 调用模式 |
| `delayMs` | long | `0` | 慢调用延迟 |

```bash
curl "http://localhost:8080/api/circuit-breaker/test-reactor?name=backendA&mode=success"
```

### GET /api/circuit-breaker/test-combo

双熔断器组合调用。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `primary` | String | `externalApi` | 主熔断器名称 |
| `secondary` | String | `secondaryApi` | 次熔断器名称 |
| `mode` | String | `success` | 调用模式 |
| `delayMs` | long | `0` | 慢调用延迟 |

```bash
curl "http://localhost:8080/api/circuit-breaker/test-combo?primary=api1&secondary=api2&mode=success"
```

### GET /api/circuit-breaker/state

查询指定熔断器状态。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | String | `externalApi` | 熔断器名称 |

```bash
curl "http://localhost:8080/api/circuit-breaker/state?name=externalApi"
```

响应示例：

```json
{
  "success": true,
  "data": {
    "name": "externalApi",
    "state": "CLOSED",
    "failureRate": -1.0,
    "slowCallRate": -1.0,
    "bufferedCalls": 0,
    "failedCalls": 0,
    "slowCalls": 0,
    "notPermittedCalls": 0
  }
}
```

### GET /api/circuit-breaker/state-combo

查询双熔断器组合状态。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `primary` | String | `externalApi` | 主熔断器名称 |
| `secondary` | String | `secondaryApi` | 次熔断器名称 |

### GET /api/circuit-breaker/states

列出所有已创建的熔断器名称。

```bash
curl "http://localhost:8080/api/circuit-breaker/states"
```

---

## 2. RateLimiterController

**基础路径**: `/api/rate-limiter`

### GET /api/rate-limiter/test

基础限流器调用测试。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `mode` | String | `success` | 调用模式：`success` / `failure` / `slow` |
| `delayMs` | long | `0` | 慢调用延迟毫秒数 |

```bash
curl "http://localhost:8080/api/rate-limiter/test?mode=success"
```

### GET /api/rate-limiter/test-fallback

带降级策略的限流器调用。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `mode` | String | `success` | 调用模式 |
| `delayMs` | long | `0` | 慢调用延迟 |

### GET /api/rate-limiter/test-reactor

指定命名限流器调用。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | String | `externalApi` | 限流器名称 |
| `mode` | String | `success` | 调用模式 |
| `delayMs` | long | `0` | 慢调用延迟 |

### GET /api/rate-limiter/test-combo

双限流器组合调用。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `primary` | String | `externalApi` | 主限流器名称 |
| `secondary` | String | `secondaryApi` | 次限流器名称 |
| `mode` | String | `success` | 调用模式 |
| `delayMs` | long | `0` | 慢调用延迟 |

### GET /api/rate-limiter/state

查询指定限流器状态。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | String | `externalApi` | 限流器名称 |

响应示例：

```json
{
  "success": true,
  "data": {
    "name": "externalApi",
    "availablePermissions": 10,
    "waitingThreads": 0
  }
}
```

### GET /api/rate-limiter/state-combo

查询双限流器组合状态。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `primary` | String | `externalApi` | 主限流器 |
| `secondary` | String | `secondaryApi` | 次限流器 |

### GET /api/rate-limiter/states

列出所有已创建的限流器名称。

---

## 3. ConfigController

**基础路径**: `/api/config`

### POST /api/config/circuit-breaker/{name}

动态更新指定熔断器配置（创建新实例替换缓存中的旧实例）。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | path | - | 熔断器名称 |
| `failureRateThreshold` | float | `50` | 失败率阈值（%） |
| `slowCallRateThreshold` | float | `50` | 慢调用率阈值（%） |
| `slidingWindowSize` | int | `10` | 滑动窗口大小 |
| `minimumNumberOfCalls` | int | `5` | 最小调用次数 |
| `waitDurationInOpenStateMs` | long | `5000` | OPEN 等待时长（ms） |

```bash
curl -X POST "http://localhost:8080/api/config/circuit-breaker/externalApi?failureRateThreshold=30&slidingWindowSize=20"
```

### GET /api/config/circuit-breaker/{name}

查询指定熔断器的当前配置。

```bash
curl "http://localhost:8080/api/config/circuit-breaker/externalApi"
```

### POST /api/config/rate-limiter/{name}

动态更新指定限流器配置。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | path | - | 限流器名称 |
| `limitForPeriod` | int | `10` | 每周期允许调用次数 |
| `limitRefreshPeriodMs` | long | `1000` | 周期刷新时间（ms） |
| `timeoutDurationMs` | long | `0` | 等待超时时间（ms） |

```bash
curl -X POST "http://localhost:8080/api/config/rate-limiter/externalApi?limitForPeriod=50"
```

### GET /api/config/rate-limiter/{name}

查询指定限流器的当前配置。

---

## 4. ProductionController

**基础路径**: `/api/production`

### GET /api/production/test

生产模式测试：CB + RL 组合保护 + 多级降级。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `mode` | String | `success` | 调用模式 |
| `delayMs` | long | `0` | 慢调用延迟 |

降级策略（按优先级）：
1. 缓存命中 → 返回 `cached:{value}`
2. CB/RL 异常 → 返回 `static-fallback`
3. 其他异常 → 返回错误响应（`DEGRADED` 状态）

```bash
curl "http://localhost:8080/api/production/test?mode=success"
curl "http://localhost:8080/api/production/test?mode=failure"
```

### GET /api/production/cache

查看降级缓存快照。

```bash
curl "http://localhost:8080/api/production/cache"
```

### DELETE /api/production/cache

清空降级缓存。

```bash
curl -X DELETE "http://localhost:8080/api/production/cache"
```

---

## 5. MetricsController

**基础路径**: `/api/metrics`

### GET /api/metrics/snapshot

获取自定义指标快照。

```bash
curl "http://localhost:8080/api/metrics/snapshot"
```

响应示例：

```json
{
  "success": true,
  "data": {
    "circuitBreakerCalls": 42.0,
    "rateLimiterCalls": 100.0,
    "apiCallCount": 142,
    "apiCallTotalTimeMs": 5230.5,
    "apiCallMeanTimeMs": 36.8,
    "apiCallMaxTimeMs": 3001.2
  }
}
```

---

## 6. Actuator 端点

Spring Boot Actuator 提供的标准端点：

| 端点 | 说明 |
|------|------|
| `GET /actuator/health` | 应用健康检查 |
| `GET /actuator/prometheus` | Prometheus 格式指标导出 |
| `GET /actuator/metrics` | Micrometer 指标列表 |
| `GET /actuator/metrics/{name}` | 查询指定指标值 |

```bash
curl "http://localhost:8080/actuator/health"
curl "http://localhost:8080/actuator/prometheus"
curl "http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state"
```

---

**文档版本**: v1.0
**最后更新**: 2026-02-20

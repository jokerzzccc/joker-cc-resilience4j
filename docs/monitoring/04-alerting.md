# 告警配置

## 1. Prometheus 告警规则

### alerts.yml

```yaml
groups:
  - name: resilience4j-circuitbreaker
    rules:
      - alert: CircuitBreakerOpen
        expr: resilience4j_circuitbreaker_state != 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "CircuitBreaker {{ $labels.name }} is not CLOSED"
          description: "CircuitBreaker {{ $labels.name }} has been in non-CLOSED state for more than 1 minute. Current state value: {{ $value }}"

      - alert: HighFailureRate
        expr: resilience4j_circuitbreaker_failure_rate > 50
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High failure rate on {{ $labels.name }}"
          description: "CircuitBreaker {{ $labels.name }} failure rate is {{ $value }}%, exceeding 50% threshold for 2 minutes"

      - alert: HighSlowCallRate
        expr: resilience4j_circuitbreaker_slow_call_rate > 50
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High slow call rate on {{ $labels.name }}"
          description: "CircuitBreaker {{ $labels.name }} slow call rate is {{ $value }}%"

  - name: resilience4j-ratelimiter
    rules:
      - alert: RateLimiterExhausted
        expr: resilience4j_ratelimiter_available_permissions == 0
        for: 30s
        labels:
          severity: warning
        annotations:
          summary: "RateLimiter {{ $labels.name }} exhausted"
          description: "RateLimiter {{ $labels.name }} has 0 available permissions for 30 seconds"

      - alert: HighWaitingThreads
        expr: resilience4j_ratelimiter_waiting_threads > 10
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "High waiting threads on {{ $labels.name }}"
          description: "RateLimiter {{ $labels.name }} has {{ $value }} waiting threads"
```

在 `prometheus.yml` 中引用：

```yaml
rule_files:
  - "alerts.yml"
```

---

## 2. Alertmanager 配置

### alertmanager.yml

```yaml
global:
  resolve_timeout: 5m

route:
  group_by: ['alertname', 'severity']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 1h
  receiver: 'default'

  routes:
    - match:
        severity: critical
      receiver: 'critical-alerts'
      repeat_interval: 5m

    - match:
        severity: warning
      receiver: 'warning-alerts'
      repeat_interval: 30m

receivers:
  - name: 'default'
    webhook_configs:
      - url: 'http://localhost:9093/webhook'

  - name: 'critical-alerts'
    webhook_configs:
      - url: 'http://localhost:9093/webhook/critical'

  - name: 'warning-alerts'
    webhook_configs:
      - url: 'http://localhost:9093/webhook/warning'
```

### Docker 启动（仅供参考）

```bash
# 注意：本项目禁止执行 Docker 命令
docker run -d \
  --name alertmanager \
  -p 9093:9093 \
  -v ./alertmanager.yml:/etc/alertmanager/alertmanager.yml \
  prom/alertmanager
```

---

## 3. 告警级别定义

| 级别 | 触发条件 | 响应时间 | 通知方式 |
|------|---------|---------|---------|
| **Critical** | 熔断器 OPEN | 立即 | 即时通知（钉钉/飞书/PagerDuty） |
| **Warning** | 失败率 > 50%，许可耗尽，等待线程过多 | 5 分钟内 | 工作群通知 |
| **Info** | 失败率 > 20%，许可低于阈值 | 工作时间处理 | 邮件/日志 |

---

## 4. 告警规则汇总

| 告警名 | 指标 | 阈值 | 持续时间 | 级别 |
|--------|------|------|---------|------|
| CircuitBreakerOpen | `state != 0` | 非 CLOSED | 1m | Critical |
| HighFailureRate | `failure_rate` | > 50% | 2m | Warning |
| HighSlowCallRate | `slow_call_rate` | > 50% | 2m | Warning |
| RateLimiterExhausted | `available_permissions` | == 0 | 30s | Warning |
| HighWaitingThreads | `waiting_threads` | > 10 | 1m | Warning |

---

## 5. 最佳实践

### 防告警疲劳

- **合理设置 `for` 持续时间**：避免瞬时波动触发告警（如网络抖动导致短暂失败率升高）
- **设置 `repeat_interval`**：Critical 每 5 分钟重复，Warning 每 30 分钟，避免频繁通知
- **分级通知**：Critical 通知值班人员，Warning 发到工作群，Info 记录日志

### 升级策略

```
告警触发 → Warning 通知 → 5 分钟未恢复 → 升级为 Critical → 通知值班人员
```

### Runbook 链接

在告警 annotation 中添加 runbook 链接，帮助值班人员快速定位和处理：

```yaml
annotations:
  runbook_url: "https://wiki.example.com/runbook/circuit-breaker-open"
```

### 环境区分

生产环境和测试环境使用不同的告警规则和阈值：

| 配置项 | 测试环境 | 生产环境 |
|--------|---------|---------|
| CircuitBreakerOpen `for` | 5m | 1m |
| HighFailureRate 阈值 | 80% | 50% |
| repeat_interval (Critical) | 30m | 5m |

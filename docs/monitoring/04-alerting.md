# 告警配置

## 1. Prometheus 告警规则

告警规则配置文件：[`monitoring/alerts.yml`](../../monitoring/alerts.yml)

Prometheus 通过 `rule_files` 引用告警规则文件，在 [`monitoring/prometheus.yml`](../../monitoring/prometheus.yml) 中已配置：

```yaml
rule_files:
  - "alerts.yml"
```

### 规则内容

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

---

## 2. Alertmanager 配置

Alertmanager 配置文件：[`monitoring/alertmanager.yml`](../../monitoring/alertmanager.yml)

Alertmanager 部署在 joker01 服务器上，通过 Docker Compose 与 Prometheus 一起启动。Prometheus 在 [`monitoring/prometheus.yml`](../../monitoring/prometheus.yml) 中已配置 Alertmanager 地址：

```yaml
alerting:
  alertmanagers:
    - static_configs:
        - targets: ['alertmanager:9093']    # Docker 内部网络
```

### 路由规则

```yaml
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
```

> 完整配置包含 3 个 receiver（default、critical-alerts、warning-alerts），使用 webhook 接收告警。部署时可替换为实际的通知渠道（钉钉、飞书、PagerDuty 等）。

访问 Alertmanager：`http://joker01:9093`

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

## 5. 部署与验证

告警组件通过 [`monitoring/docker-compose.yml`](../../monitoring/docker-compose.yml) 一键部署到 joker01。

### 验证步骤

1. 访问 `http://joker01:9090/alerts` 查看 Prometheus 告警规则是否加载
2. 访问 `http://joker01:9093` 查看 Alertmanager 是否正常运行
3. 触发测试告警：让熔断器进入 OPEN 状态，观察告警是否触发

### 配置热重载

修改 `alerts.yml` 后无需重启 Prometheus：

```bash
# 在 joker01 上执行
curl -X POST http://joker01:9090/-/reload
```

---

## 6. 最佳实践

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

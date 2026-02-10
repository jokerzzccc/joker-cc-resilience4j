# Prometheus 集成

## 1. Actuator 端点

Spring Boot Actuator 自动暴露 Prometheus 格式的指标端点：

```
GET /actuator/prometheus
```

`application.yml` 配置：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

访问示例：

```bash
curl http://localhost:8080/actuator/prometheus
```

输出示例（截取 resilience4j 相关）：

```
# HELP resilience4j_circuitbreaker_state CircuitBreaker state
# TYPE resilience4j_circuitbreaker_state gauge
resilience4j_circuitbreaker_state{name="externalApi",} 0.0

# HELP resilience4j_ratelimiter_available_permissions Available permissions
# TYPE resilience4j_ratelimiter_available_permissions gauge
resilience4j_ratelimiter_available_permissions{name="externalApi",} 10.0
```

---

## 2. Prometheus 配置

### prometheus.yml

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'resilience4j-learning'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 5s
    static_configs:
      - targets: ['localhost:8080']
```

### Docker 启动（仅供参考）

```bash
# 注意：本项目禁止执行 Docker 命令，以下仅作文档示例
docker run -d \
  --name prometheus \
  -p 9090:9090 \
  -v ./prometheus.yml:/etc/prometheus/prometheus.yml \
  prom/prometheus
```

启动后访问：`http://localhost:9090`

---

## 3. PromQL 查询示例

### CircuitBreaker 查询

**当前状态**：

```promql
resilience4j_circuitbreaker_state{name="externalApi"}
```

返回值：`0` = CLOSED，`1` = OPEN，`2` = HALF_OPEN

**失败率**：

```promql
resilience4j_circuitbreaker_failure_rate{name="externalApi"}
```

**5 分钟内失败调用变化率**：

```promql
rate(resilience4j_circuitbreaker_failed_calls{name="externalApi"}[5m])
```

**熔断器状态变化（非 CLOSED）**：

```promql
resilience4j_circuitbreaker_state{name=~".*"} != 0
```

### RateLimiter 查询

**可用许可数**：

```promql
resilience4j_ratelimiter_available_permissions{name="externalApi"}
```

**等待线程数**：

```promql
resilience4j_ratelimiter_waiting_threads{name="externalApi"}
```

**许可耗尽检测**：

```promql
resilience4j_ratelimiter_available_permissions{name=~".*"} == 0
```

### 自定义指标查询

**服务调用总数**：

```promql
resilience4j_custom_circuitbreaker_calls_total
resilience4j_custom_ratelimiter_calls_total
```

**API 调用平均耗时**：

```promql
rate(resilience4j_custom_api_call_duration_seconds_sum[5m])
/ rate(resilience4j_custom_api_call_duration_seconds_count[5m])
```

---

## 4. 最佳实践

| 配置项 | 建议值 | 说明 |
|--------|--------|------|
| `scrape_interval` | 5-15s | 生产环境 15s，调试可缩短到 5s |
| `evaluation_interval` | 15s | 告警规则评估间隔 |
| `retention` | 15d | 默认保留 15 天数据 |
| `metrics_path` | `/actuator/prometheus` | Spring Boot Actuator 默认路径 |

- Prometheus 使用 pull 模式，应用无需主动推送
- 确保 `/actuator/prometheus` 端点可从 Prometheus 服务器访问
- 生产环境建议配置服务发现（如 Consul、Kubernetes SD）替代静态配置

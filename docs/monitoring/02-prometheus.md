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

## 2. 远程 Prometheus 抓取

本项目与部署在 **joker01** 服务器上的 Prometheus 实例集成。Prometheus 通过 pull 模式从应用的公网 IP 抓取指标。

### 网络拓扑

```
本机（Spring Boot 应用）              joker01 服务器
┌──────────────────────┐         ┌──────────────────────┐
│ :8080  业务端口       │◄────────│ Prometheus :9090     │
│ :9091  Actuator(prod)│  scrape │ Grafana    :3000     │
└──────────────────────┘         │ Alertmanager :9093   │
                                 └──────────────────────┘
```

### 前提条件

1. 本机应用已启动，`/actuator/prometheus` 端点可从 joker01 访问
2. 本机防火墙放行 8080 端口（dev）或 9091 端口（prod）
3. 验证连通性：从 joker01 执行 `curl http://<APP_PUBLIC_IP>:8080/actuator/prometheus`

### 生产环境端口分离

`application-prod.yml` 配置了 Actuator 独立端口：

```yaml
management:
  server:
    port: 9091
```

启用 prod profile 后，Prometheus 应抓取 `:9091/actuator/prometheus`，业务流量走 `:8080`。

---

## 3. Prometheus 配置

### prometheus.yml

完整配置文件位于项目根目录 [`monitoring/prometheus.yml`](../../monitoring/prometheus.yml)：

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - "alerts.yml"

alerting:
  alertmanagers:
    - static_configs:
        - targets: ['alertmanager:9093']

scrape_configs:
  - job_name: 'resilience4j-learning'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 5s
    static_configs:
      - targets: ['<APP_PUBLIC_IP>:8080']
        labels:
          application: 'resilience4j-reactor-learning'
          environment: 'dev'
```

> **部署说明**：将 `<APP_PUBLIC_IP>` 替换为运行 Spring Boot 应用的机器公网 IP。
> 完整部署指南见 [`monitoring/README.md`](../../monitoring/README.md)。

### Docker Compose 一键部署（joker01 服务器）

```bash
# 1. 复制配置到 joker01
scp -r monitoring/ user@joker01:~/monitoring/

# 2. 修改 prometheus.yml 中 <APP_PUBLIC_IP>

# 3. 启动
cd ~/monitoring && docker compose up -d
```

> 注意：本项目禁止在开发机上执行 Docker 命令，以上命令在 joker01 服务器上执行。

---

## 4. Pull 模式与 Push 模式

### Pull 模式（Prometheus 默认）

```
Prometheus Server                        应用（Spring Boot）
┌─────────────┐    定时 HTTP GET          ┌──────────────────────┐
│             │ ──────────────────────►   │ /actuator/prometheus │
│  scrape_job │    每 5-15s 一次          │                      │
│             │ ◄──────────────────────   │  MeterRegistry       │
│  TSDB 存储   │    返回文本格式指标        │  → 遍历所有 Meter     │
└─────────────┘                          │  → 格式化输出         │
                                         └──────────────────────┘
```

**Prometheus 主动拉取**，应用被动响应。应用只需暴露一个 HTTP 端点，Prometheus 定时来取。

本项目就是这种模式 — joker01 上的 Prometheus 每 5s 向 `<APP_PUBLIC_IP>:8080/actuator/prometheus` 发起 GET 请求。

### Push 模式（Pushgateway）

```
应用                     Pushgateway              Prometheus Server
┌──────────┐  主动推送    ┌──────────────┐  pull    ┌─────────────┐
│ 批处理任务 │ ─────────► │ :9091        │ ◄─────── │             │
│ 短生命周期 │  HTTP POST │ 暂存指标数据  │          │  scrape_job │
└──────────┘            └──────────────┘          └─────────────┘
```

**应用主动推送**指标到 Pushgateway 中间节点，Prometheus 再从 Pushgateway pull。

### 对比

| 维度 | Pull 模式 | Push 模式（Pushgateway） |
|------|----------|------------------------|
| **发起方** | Prometheus 主动拉取 | 应用主动推送 |
| **应用感知** | 应用不需要知道 Prometheus 的存在 | 应用需要知道 Pushgateway 地址 |
| **存活检测** | scrape 失败即知目标挂了（`up` 指标自动为 0） | 目标挂了但旧数据仍留在 Pushgateway，无法区分 |
| **适用场景** | 长时间运行的服务（Web 应用、微服务） | 短生命周期任务（批处理、CronJob、Lambda） |
| **网络要求** | Prometheus 能访问应用端口 | 应用能访问 Pushgateway 端口 |
| **扩展性** | 配合服务发现（Consul/K8s SD）自动注册 | 需要应用自行管理推送逻辑 |

### 为什么 Prometheus 选 Pull 作为默认模式

1. **简单性** — 应用只需暴露 HTTP 端点，不需要引入推送客户端
2. **可靠的存活检测** — scrape 失败 = 目标不可用，`up` 指标自动反映健康状态
3. **避免推送风暴** — 不会因为大量实例同时推送而压垮监控系统
4. **中心化控制** — 采集频率、目标列表由 Prometheus 统一管理，应用无需配置

### 外层 Pull 与内层数据写入的关系

Prometheus 对应用的 pull 是**外层**通信方式，与应用内部指标的数据写入方式是独立的两层：

```
                        应用内部                              应用外部
                 ┌─────────────────────────────────────┐
                 │                                     │
  业务调用 ────► │  CircuitBreaker                      │
                 │    ├─ 事件推送 → Tagged Counter/Timer │    Prometheus
                 │    └─ 内部状态 ← Gauge lambda 拉取    │ ◄── pull ──
                 │                                     │   (HTTP GET)
                 │  自定义 Counter ← 手动 increment()    │
                 │                                     │
                 └─────────────────────────────────────┘
```

| 层级 | 推送/拉取 | 说明 |
|------|----------|------|
| **Prometheus ↔ 应用** | Pull | Prometheus 定时 GET `/actuator/prometheus` |
| **Tagged\*Metrics 内部** | 推送式（事件驱动） | CircuitBreaker 调用完成 → 发布事件 → EventConsumer 主动更新 Counter |
| **Gauge 内部** | 拉取式（lambda） | Prometheus scrape 触发 → MeterRegistry 执行 lambda → 读取对象最新状态 |
| **自定义 Counter/Timer** | 推送式（手动调用） | 业务代码主动调用 `increment()` / `record()` 写入数据 |

不管内部用哪种方式写入数据，对外都是 Prometheus 来 pull。

> 本项目同时实现了 Push 模式（Pushgateway）DEMO，完整说明见 [05-pushgateway.md](05-pushgateway.md)。

---

## 5. PromQL 查询示例

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

## 6. 最佳实践

| 配置项 | 建议值 | 说明 |
|--------|--------|------|
| `scrape_interval` | 5-15s | 生产环境 15s，调试可缩短到 5s |
| `evaluation_interval` | 15s | 告警规则评估间隔 |
| `retention` | 15d | 默认保留 15 天数据 |
| `metrics_path` | `/actuator/prometheus` | Spring Boot Actuator 默认路径 |

- Prometheus 使用 pull 模式，应用无需主动推送
- 确保 `/actuator/prometheus` 端点可从 Prometheus 服务器访问
- 生产环境建议将 Actuator 端口与业务端口分离（`management.server.port`）
- 生产环境建议配置服务发现（如 Consul、Kubernetes SD）替代静态配置

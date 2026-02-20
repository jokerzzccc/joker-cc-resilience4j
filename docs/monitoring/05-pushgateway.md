# Pushgateway 集成

## 1. Pushgateway 概述

Pushgateway 是 Prometheus 生态中的一个中间组件，用于接收应用主动推送的指标，再由 Prometheus 从 Pushgateway 拉取。

### 适用场景

| 场景 | 说明 |
|------|------|
| 批处理任务 | 任务执行完就退出，Prometheus 来不及 scrape |
| CronJob / Lambda | 短生命周期，没有常驻的 HTTP 端点 |
| 防火墙限制 | Prometheus 无法主动访问应用，但应用可以访问 Pushgateway |

### 数据流

```
应用（Spring Boot）                  Pushgateway              Prometheus
┌──────────────────────┐            ┌──────────────┐         ┌─────────────┐
│ PrometheusMeterRegistry│  POST     │              │  pull   │             │
│   .scrape()           │ ────────► │  :9091       │ ◄────── │  :9090      │
│                       │  指标文本  │  暂存指标数据 │ scrape  │  TSDB 存储  │
│ WebClient POST        │           │              │         │             │
└──────────────────────┘            └──────────────┘         └─────────────┘
```

### 与 Pull 模式的对比

| 维度 | Pull 模式 | Push 模式（Pushgateway） |
|------|----------|------------------------|
| 发起方 | Prometheus 主动拉取 | 应用主动推送 |
| 应用感知 | 应用不需要知道 Prometheus 的存在 | 应用需要知道 Pushgateway 地址 |
| 存活检测 | scrape 失败即知目标挂了 | 目标挂了但旧数据仍留在 Pushgateway |
| 适用场景 | 长时间运行的服务 | 短生命周期任务 |

---

## 2. 实现方案

### 为什么不用 Spring Boot 自动配置？

Spring Boot 3.3 升级到 Prometheus Client 1.x，`PrometheusPushGatewayManager` 自动配置不再生效。需要降级到 `micrometer-registry-prometheus-simpleclient`（即将在 Spring Boot 3.5 移除）。

本项目采用**零额外依赖**方案：

```
PrometheusMeterRegistry.scrape()  →  获取 Prometheus 文本格式
WebClient POST                    →  推送到 Pushgateway REST API
```

- 使用项目已有的 `WebClient`（Spring WebFlux 自带）
- 直接调用 Pushgateway 的 REST API
- 保持全链路响应式（Mono/Flux）

### 核心代码

**PushgatewayService** — 推送逻辑：

```java
public class PushgatewayService {

    private final PrometheusMeterRegistry meterRegistry;
    private final WebClient webClient;

    public Mono<ApiResponse<String>> push() {
        // 1. 获取所有指标的 Prometheus 文本格式
        String metricsText = meterRegistry.scrape();

        // 2. POST 到 Pushgateway REST API
        return webClient.post()
                .uri("/metrics/job/{job}/instance/{instance}", jobName, instanceName)
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(metricsText)
                .retrieve()
                .toBodilessEntity()
                .map(response -> ApiResponse.success("Pushed to Pushgateway"));
    }

    public Mono<ApiResponse<String>> delete() {
        return webClient.delete()
                .uri("/metrics/job/{job}/instance/{instance}", jobName, instanceName)
                .retrieve()
                .toBodilessEntity()
                .map(response -> ApiResponse.success("Deleted from Pushgateway"));
    }
}
```

**PushgatewayConfig** — 条件注入 + 定时推送：

```java
@Configuration
@ConditionalOnProperty(name = "app.pushgateway.enabled", havingValue = "true")
public class PushgatewayConfig {

    @Bean
    public PushgatewayService pushgatewayService(
            PrometheusMeterRegistry meterRegistry,
            PushgatewayProperties properties) {
        // 创建 WebClient 指向 Pushgateway
        // 启动 Flux.interval 定时推送
    }
}
```

定时推送使用 Reactor `Flux.interval`（而非 `@Scheduled`），保持响应式风格。

---

## 3. Pushgateway REST API

Pushgateway 提供简单的 HTTP API 管理指标分组：

| 操作 | 方法 | 端点 | 说明 |
|------|------|------|------|
| 推送指标 | `POST` | `/metrics/job/<job>/instance/<instance>` | 追加/更新该分组的指标 |
| 删除指标 | `DELETE` | `/metrics/job/<job>/instance/<instance>` | 删除该分组的所有指标 |
| 查看所有 | `GET` | `/metrics` | 查看 Pushgateway 上暂存的所有指标 |

**Body 格式**：标准 Prometheus 文本格式，每行以 `\n` 结尾：

```
# HELP resilience4j_circuitbreaker_state CircuitBreaker state
# TYPE resilience4j_circuitbreaker_state gauge
resilience4j_circuitbreaker_state{name="externalApi"} 0.0
```

**分组（Grouping Key）**：`job` + `instance` 组成分组键，同一分组内的指标在推送时整体替换。

---

## 4. 配置

### application.yml（默认禁用）

```yaml
app:
  pushgateway:
    enabled: false
    url: http://localhost:9091
    job-name: resilience4j-learning
    push-interval-seconds: 15
```

### application-pushgateway.yml（启用 profile）

```yaml
app:
  pushgateway:
    enabled: true
    url: http://<PUSHGATEWAY_HOST>:9092
```

### 启动方式

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=pushgateway
```

---

## 5. 部署

### Docker Compose（joker01 服务器）

[`monitoring/docker-compose.yml`](../../monitoring/docker-compose.yml) 已包含 Pushgateway 服务：

```yaml
pushgateway:
  image: prom/pushgateway:latest
  ports:
    - "9092:9091"    # 映射为 9092，避免与 prod Actuator 端口冲突
```

[`monitoring/prometheus.yml`](../../monitoring/prometheus.yml) 已配置 Pushgateway scrape job：

```yaml
- job_name: 'pushgateway'
  honor_labels: true         # 保留推送时携带的 job/instance 标签
  static_configs:
    - targets: ['pushgateway:9091']
```

> `honor_labels: true` 确保 Prometheus 不会用自己的 `job`、`instance` 标签覆盖推送时携带的值。

---

## 6. 验证

### 手动推送

```bash
# 通过应用 API 触发推送
curl -X POST http://localhost:8080/api/pushgateway/push

# 查看推送状态
curl http://localhost:8080/api/pushgateway/status

# 删除 Pushgateway 上的指标
curl -X DELETE http://localhost:8080/api/pushgateway/delete
```

### 直接用 curl 推送到 Pushgateway

```bash
# 推送单个指标到 Pushgateway
echo 'demo_metric 42' | curl --data-binary @- http://joker01:9092/metrics/job/test/instance/local

# 查看 Pushgateway 上的所有指标
curl http://joker01:9092/metrics
```

### 验证 Prometheus 抓取

访问 `http://joker01:9090/targets`，确认 `pushgateway` job 状态为 `UP`。

在 Prometheus 查询 Pushgateway 转发的指标：

```promql
resilience4j_circuitbreaker_state{job="resilience4j-learning"}
```

---

## 7. 注意事项

- **Pushgateway 不适合替代 Pull 模式**：对于长时间运行的服务，应优先使用 Pull 模式
- **指标不会自动过期**：推送到 Pushgateway 的指标会一直保留，直到被 DELETE 删除或 Pushgateway 重启
- **存活检测失效**：Pushgateway 始终在线，即使推送源已停止，Prometheus 仍会抓到旧数据
- **分组替换**：每次 POST 会替换整个分组（job + instance）的指标，不是追加
- **`honor_labels` 必须开启**：否则 Prometheus 会将推送指标的 `job` 标签覆盖为 `pushgateway`

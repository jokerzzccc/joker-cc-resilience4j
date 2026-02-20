# Grafana 可视化

## 1. 部署方式

Grafana 部署在 **joker01** 服务器上，通过 Docker Compose 与 Prometheus 一起启动。

完整部署配置见 [`monitoring/docker-compose.yml`](../../monitoring/docker-compose.yml)。

访问地址：`http://joker01:3000`，默认账号 `admin/admin`。

### 自动配置（Provisioning）

项目提供了 Grafana Provisioning 配置，启动后自动完成数据源和 Dashboard 加载，无需手动配置：

```
monitoring/grafana/
├── provisioning/
│   ├── datasources/prometheus.yml    # 自动配置 Prometheus 数据源
│   └── dashboards/dashboard.yml      # 自动加载 Dashboard 文件
└── dashboards/
    └── resilience4j.json             # Resilience4j 监控面板
```

**数据源配置** [`monitoring/grafana/provisioning/datasources/prometheus.yml`](../../monitoring/grafana/provisioning/datasources/prometheus.yml)：

```yaml
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090    # Docker 内部网络，Grafana 通过服务名访问 Prometheus
    isDefault: true
```

> `http://prometheus:9090` 是 Docker Compose 内部网络地址，Grafana 和 Prometheus 在同一 Docker 网络中，通过服务名直接通信。

**Dashboard 自动加载** [`monitoring/grafana/provisioning/dashboards/dashboard.yml`](../../monitoring/grafana/provisioning/dashboards/dashboard.yml)：

```yaml
apiVersion: 1
providers:
  - name: 'Resilience4j'
    type: file
    options:
      path: /var/lib/grafana/dashboards    # 容器内路径，由 docker-compose 挂载
```

---

## 2. Dashboard 面板配置

完整 Dashboard JSON 见 [`monitoring/grafana/dashboards/resilience4j.json`](../../monitoring/grafana/dashboards/resilience4j.json)，包含以下 7 个面板：

### Panel 1: CircuitBreaker 状态时间线

| 配置项 | 值 |
|--------|-----|
| 可视化 | State Timeline |
| PromQL | `resilience4j_circuitbreaker_state{name=~"$instance"}` |
| 值映射 | 0 → CLOSED (绿), 1 → OPEN (红), 2 → HALF_OPEN (黄) |

### Panel 2: 失败率仪表盘

| 配置项 | 值 |
|--------|-----|
| 可视化 | Gauge |
| PromQL | `resilience4j_circuitbreaker_failure_rate{name=~"$instance"}` |
| 阈值 | 0-30 绿，30-50 黄，50-100 红 |
| 单位 | Percent (0-100) |

### Panel 3: 慢调用率仪表盘

| 配置项 | 值 |
|--------|-----|
| 可视化 | Gauge |
| PromQL | `resilience4j_circuitbreaker_slow_call_rate{name=~"$instance"}` |
| 阈值 | 0-20 绿，20-50 黄，50-100 红 |

### Panel 4: 缓冲/失败调用趋势

| 配置项 | 值 |
|--------|-----|
| 可视化 | Time Series |
| PromQL (缓冲) | `resilience4j_circuitbreaker_buffered_calls{name=~"$instance"}` |
| PromQL (失败) | `resilience4j_circuitbreaker_failed_calls{name=~"$instance"}` |

### Panel 5: RateLimiter 可用许可

| 配置项 | 值 |
|--------|-----|
| 可视化 | Time Series |
| PromQL | `resilience4j_ratelimiter_available_permissions{name=~"$instance"}` |
| 阈值 | 0 红，2 黄，5 绿 |

### Panel 6: RateLimiter 等待线程

| 配置项 | 值 |
|--------|-----|
| 可视化 | Time Series |
| PromQL | `resilience4j_ratelimiter_waiting_threads{name=~"$instance"}` |
| 阈值 | 0 绿，5 黄，10 红 |

### Panel 7: 不被允许的调用

| 配置项 | 值 |
|--------|-----|
| 可视化 | Time Series |
| PromQL | `resilience4j_circuitbreaker_not_permitted_calls{name=~"$instance"}` |

---

## 3. Dashboard 变量

配置 Dashboard 变量实现实例筛选：

| 变量名 | 类型 | 查询 |
|--------|------|------|
| `instance` | Query | `label_values(resilience4j_circuitbreaker_state, name)` |

使用方式：在 PromQL 中用 `{name=~"$instance"}` 引用。

---

## 4. 手动导入 Dashboard

如果不使用 Provisioning 自动加载，也可以手动导入：

1. 访问 `http://joker01:3000`
2. Dashboards → Import → Upload JSON file
3. 选择 [`monitoring/grafana/dashboards/resilience4j.json`](../../monitoring/grafana/dashboards/resilience4j.json)
4. 选择 Prometheus 数据源 → Import

---

## 5. 最佳实践

- **刷新间隔**：Dashboard 刷新设为 5-10s，与 Prometheus scrape interval 匹配
- **阈值着色**：失败率、慢调用率使用红黄绿三色阈值，直观标识健康状态
- **变量筛选**：使用 Dashboard 变量按实例名过滤，避免一个面板显示过多曲线
- **告警集成**：建议使用 Prometheus Alertmanager 而非 Grafana 内置告警（见 [04-alerting.md](04-alerting.md)）
- **Provisioning 优先**：使用 provisioning 配置自动加载数据源和 Dashboard，避免手动配置丢失

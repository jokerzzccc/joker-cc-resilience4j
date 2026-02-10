# Grafana 可视化

## 1. 启动 Grafana（仅供参考）

```bash
# 注意：本项目禁止执行 Docker 命令，以下仅作文档示例
docker run -d \
  --name grafana \
  -p 3000:3000 \
  grafana/grafana
```

默认账号：`admin/admin`，访问：`http://localhost:3000`

添加数据源：Configuration → Data Sources → Add → Prometheus → URL: `http://localhost:9090`

---

## 2. Dashboard 面板配置

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

### Panel 3: 调用速率

| 配置项 | 值 |
|--------|-----|
| 可视化 | Time Series |
| PromQL (成功) | `rate(resilience4j_circuitbreaker_buffered_calls{name=~"$instance"}[1m])` |
| PromQL (失败) | `rate(resilience4j_circuitbreaker_failed_calls{name=~"$instance"}[1m])` |

### Panel 4: RateLimiter 可用许可

| 配置项 | 值 |
|--------|-----|
| 可视化 | Time Series |
| PromQL | `resilience4j_ratelimiter_available_permissions{name=~"$instance"}` |
| 阈值 | 低于 2 时红色填充 |

### Panel 5: RateLimiter 等待线程

| 配置项 | 值 |
|--------|-----|
| 可视化 | Time Series |
| PromQL | `resilience4j_ratelimiter_waiting_threads{name=~"$instance"}` |
| 阈值 | 大于 5 时黄色，大于 10 时红色 |

### Panel 6: 慢调用率

| 配置项 | 值 |
|--------|-----|
| 可视化 | Gauge |
| PromQL | `resilience4j_circuitbreaker_slow_call_rate{name=~"$instance"}` |
| 阈值 | 0-20 绿，20-50 黄，50-100 红 |

---

## 3. Dashboard 变量

配置 Dashboard 变量实现实例筛选：

| 变量名 | 类型 | 查询 |
|--------|------|------|
| `instance` | Query | `label_values(resilience4j_circuitbreaker_state, name)` |

使用方式：在 PromQL 中用 `{name=~"$instance"}` 引用。

---

## 4. Dashboard JSON 模板

以下 JSON 可直接导入 Grafana（Dashboards → Import → Paste JSON）：

```json
{
  "dashboard": {
    "title": "Resilience4j Monitoring",
    "tags": ["resilience4j"],
    "timezone": "browser",
    "refresh": "5s",
    "templating": {
      "list": [
        {
          "name": "instance",
          "type": "query",
          "query": "label_values(resilience4j_circuitbreaker_state, name)",
          "datasource": "Prometheus",
          "refresh": 2
        }
      ]
    },
    "panels": [
      {
        "title": "CircuitBreaker State",
        "type": "state-timeline",
        "gridPos": { "h": 6, "w": 12, "x": 0, "y": 0 },
        "targets": [
          {
            "expr": "resilience4j_circuitbreaker_state{name=~\"$instance\"}",
            "legendFormat": "{{name}}"
          }
        ],
        "fieldConfig": {
          "defaults": {
            "mappings": [
              { "type": "value", "options": { "0": { "text": "CLOSED", "color": "green" } } },
              { "type": "value", "options": { "1": { "text": "OPEN", "color": "red" } } },
              { "type": "value", "options": { "2": { "text": "HALF_OPEN", "color": "yellow" } } }
            ]
          }
        }
      },
      {
        "title": "Failure Rate",
        "type": "gauge",
        "gridPos": { "h": 6, "w": 6, "x": 12, "y": 0 },
        "targets": [
          {
            "expr": "resilience4j_circuitbreaker_failure_rate{name=~\"$instance\"}",
            "legendFormat": "{{name}}"
          }
        ],
        "fieldConfig": {
          "defaults": {
            "unit": "percent",
            "min": 0,
            "max": 100,
            "thresholds": {
              "steps": [
                { "value": 0, "color": "green" },
                { "value": 30, "color": "yellow" },
                { "value": 50, "color": "red" }
              ]
            }
          }
        }
      },
      {
        "title": "RateLimiter Available Permissions",
        "type": "timeseries",
        "gridPos": { "h": 6, "w": 12, "x": 0, "y": 6 },
        "targets": [
          {
            "expr": "resilience4j_ratelimiter_available_permissions{name=~\"$instance\"}",
            "legendFormat": "{{name}}"
          }
        ]
      },
      {
        "title": "Waiting Threads",
        "type": "timeseries",
        "gridPos": { "h": 6, "w": 6, "x": 12, "y": 6 },
        "targets": [
          {
            "expr": "resilience4j_ratelimiter_waiting_threads{name=~\"$instance\"}",
            "legendFormat": "{{name}}"
          }
        ]
      }
    ]
  }
}
```

---

## 5. 最佳实践

- **刷新间隔**：Dashboard 刷新设为 5-10s，与 Prometheus scrape interval 匹配
- **阈值着色**：失败率、慢调用率使用红黄绿三色阈值，直观标识健康状态
- **变量筛选**：使用 Dashboard 变量按实例名过滤，避免一个面板显示过多曲线
- **告警集成**：Grafana 告警可直接基于 Panel 查询配置，但建议使用 Prometheus Alertmanager（见 04-alerting.md）

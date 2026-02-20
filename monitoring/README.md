# 监控组件部署指南

本目录包含 Prometheus + Grafana + Alertmanager + Pushgateway 的完整配置文件，用于部署到 joker01 远程服务器。

## 前置条件

- joker01 服务器已安装 Docker 和 Docker Compose
- 本机 Spring Boot 应用已启动，`/actuator/prometheus` 端点可从 joker01 访问
- 本机防火墙已放行 8080 端口（或 prod 环境的 9091 端口）

## 目录结构

```
monitoring/
├── prometheus.yml                              # Prometheus 主配置
├── alerts.yml                                  # 告警规则
├── alertmanager.yml                            # Alertmanager 配置
├── docker-compose.yml                          # 一键启动
├── grafana/
│   ├── provisioning/
│   │   ├── datasources/prometheus.yml          # 自动配置 Prometheus 数据源
│   │   └── dashboards/dashboard.yml            # 自动加载 Dashboard
│   └── dashboards/
│       └── resilience4j.json                   # Resilience4j Dashboard
└── README.md                                   # 本文件
```

## 部署步骤

### 1. 复制配置到 joker01

```bash
scp -r monitoring/ user@joker01:~/monitoring/
```

### 2. 修改 Prometheus target

编辑 joker01 上的 `~/monitoring/prometheus.yml`，将 `<APP_PUBLIC_IP>` 替换为运行 Spring Boot 应用的机器公网 IP：

```yaml
static_configs:
  - targets: ['你的公网IP:8080']
```

### 3. 启动监控组件

```bash
# 在 joker01 上执行
cd ~/monitoring
docker compose up -d
```

### 4. 验证

| 组件 | 地址 | 说明 |
|------|------|------|
| Prometheus | `http://joker01:9090` | 检查 Targets 页面（Status → Targets） |
| Grafana | `http://joker01:3000` | 默认账号 `admin/admin` |
| Alertmanager | `http://joker01:9093` | 告警管理 |
| Pushgateway | `http://joker01:9092` | 指标推送接收（端口 9092） |

### 5. 验证 Prometheus 抓取

访问 `http://joker01:9090/targets`，确认 `resilience4j-learning` job 状态为 `UP`。

如果状态为 `DOWN`，检查：
- 本机应用是否已启动
- 本机防火墙是否放行 8080 端口
- 从 joker01 执行 `curl http://<APP_PUBLIC_IP>:8080/actuator/prometheus` 是否有输出

## 生产环境配置

启用 `prod` profile 后，Actuator 使用独立端口 9091：

```bash
# 本机启动应用（prod 模式）
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

修改 `prometheus.yml` 中 target 端口为 9091，或取消注释 `resilience4j-learning-prod` job。

## Pushgateway

Pushgateway 接收应用主动推送的指标。启动后，应用可通过 `POST /metrics/job/<job>/instance/<instance>` 推送指标。

### 启用应用推送

```bash
# 本机启动应用（pushgateway 模式）
mvn spring-boot:run -Dspring-boot.run.profiles=pushgateway
```

启动前修改 `application-pushgateway.yml` 中 `<PUSHGATEWAY_HOST>` 为 joker01 地址。

### 验证

```bash
# 手动推送
curl -X POST http://localhost:8080/api/pushgateway/push

# 查看 Pushgateway 上的指标
curl http://joker01:9092/metrics

# 确认 Prometheus 抓取 Pushgateway
# 访问 http://joker01:9090/targets，pushgateway job 应为 UP
```

详细说明见 [`docs/monitoring/05-pushgateway.md`](docs/monitoring/05-pushgateway.md)。

## 常用操作

```bash
# 查看日志
docker compose logs -f prometheus
docker compose logs -f grafana

# 重启（修改配置后）
docker compose restart prometheus

# Prometheus 热重载配置（无需重启）
curl -X POST http://joker01:9090/-/reload

# 停止所有组件
docker compose down

# 停止并清除数据
docker compose down -v
```

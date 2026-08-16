# 分布式维护内部 API

## 通用约定

- 调用范围：协调后端 C 与 SaaS、车险业务后端之间的内部通信。
- 请求头：`X-Maintenance-Secret`，生产环境必须由三个后端配置相同的高强度密钥。
- 内容类型：`application/json`。
- 所有周期命令都携带 `runId`；旧周期和处于单机模式后的迟到请求会被拒绝。
- 维护接口不计入业务请求排空计数，但仍进行独立鉴权和任务幂等校验。

统一参与端响应：

```json
{
  "accepted": true,
  "message": "ready",
  "state": "READY",
  "taskResult": null
}
```

## C 调用业务服务

### 开始维护

- 方法与路径：`POST /internal/maintenance/start`
- 作用：阻止新业务请求、排空在途业务并进入 `READY`。

```json
{
  "runId": "2026-08-16T04:00:00-uuid",
  "businessDate": "2026-08-15",
  "leaseTimeoutSeconds": 900
}
```

常见失败：已有其他周期、已进入单机模式、请求排空超时。

### 维护心跳

- 方法与路径：`POST /internal/maintenance/heartbeat`
- 作用：续租当前联机维护周期。

```json
{ "runId": "2026-08-16T04:00:00-uuid" }
```

### 执行任务

- 方法与路径：`POST /internal/maintenance/execute`
- 作用：执行目标服务中预先注册的任务编号。
- 幂等键：当前实现为 `runId + taskId`。

```json
{
  "runId": "2026-08-16T04:00:00-uuid",
  "taskId": "saas-workorder-overage",
  "businessDate": "2026-08-15",
  "deadlineEpochMillis": 1786830000000
}
```

成功响应：

```json
{
  "accepted": true,
  "message": "task succeeded",
  "state": "READY",
  "taskResult": "SUCCEEDED"
}
```

### 请求取消超时任务

- 方法与路径：`POST /internal/maintenance/cancel`
- 作用：为指定任务设置协作式取消标志，不强制终止线程，也不影响同组任务。

```json
{
  "runId": "2026-08-16T04:00:00-uuid",
  "taskId": "saas-workorder-overage"
}
```

### 结束维护

- 方法与路径：`POST /internal/maintenance/finish`
- 作用：联机 `READY` 服务确认无活动任务后恢复业务。
- 单机服务只记录并拒绝该命令，不改变本地任务或退出时间。

```json
{ "runId": "2026-08-16T04:00:00-uuid" }
```

### 查询参与端状态

- 方法与路径：`GET /internal/maintenance/status`
- 作用：返回业务服务当前维护状态。

## C 的运维接口

### 查询协调状态

- 方法与路径：`GET /internal/coordinator/status`

```json
{
  "runId": "2026-08-16T04:00:00-uuid",
  "phase": "EXECUTING",
  "running": true,
  "readyServices": ["insurance-backend", "saas-backend"],
  "tasks": {
    "saas-workorder-overage": "SUCCEEDED",
    "saas-balance-access": "DISPATCHED"
  }
}
```

### 手工触发维护

- 方法与路径：`POST /internal/coordinator/run`
- 作用：使用当前配置立即执行一次维护；已有周期运行时返回 `accepted=false`。

```json
{ "accepted": true }
```

## 环境参数

开发环境默认：

- C：`http://localhost:8082`
- SaaS：`http://localhost:8081`
- Insurance：`http://localhost:8080`
- 准备期限：120 秒
- 维护租约：300 秒
- 心跳：30 秒
- 单机退出等待：60 秒
- 单机兜底：04:05

生产环境默认：

- 准备期限：900 秒
- 维护租约：900 秒
- 心跳：60 秒
- 单机退出等待：900 秒
- 单机兜底：04:15
- `MAINTENANCE_INTERNAL_SECRET` 必须显式配置。
- 服务地址通过 `SAAS_MAINTENANCE_BASE_URL` 和 `INSURANCE_MAINTENANCE_BASE_URL` 配置。

主要环境变量：

| 变量 | 作用 |
| --- | --- |
| `MAINTENANCE_COORDINATOR_ENABLED` | 是否启用 C 的定时维护 |
| `MAINTENANCE_COORDINATOR_CRON` | C 的维护 cron |
| `MAINTENANCE_INTERNAL_SECRET` | 三个后端共享的内部维护密钥 |
| `MAINTENANCE_PREPARE_TIMEOUT_SECONDS` | C 等待服务 READY 的期限 |
| `MAINTENANCE_LEASE_TIMEOUT_SECONDS` | C 失联判定期限 |
| `MAINTENANCE_HEARTBEAT_INTERVAL_MS` | C 心跳周期 |
| `MAINTENANCE_FINISH_TIMEOUT_SECONDS` | C 重试 FINISH 并等待服务安全释放的期限 |
| `MAINTENANCE_DRAIN_TIMEOUT_SECONDS` | 参与端排空业务请求的期限 |
| `MAINTENANCE_STANDALONE_EXIT_DELAY_SECONDS` | 单机任务结束后的固定等待时间 |
| `MAINTENANCE_FALLBACK_CRON` | 参与端未收到 START 时的单机兜底时间 |
| `SAAS_MAINTENANCE_BASE_URL` | C 访问 SaaS 后端的基础地址 |
| `INSURANCE_MAINTENANCE_BASE_URL` | C 访问车险后端的基础地址 |

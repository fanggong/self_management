# plan_v0.1.1 统一后端落地计划

版本：v0.1.1  
日期：2026-03-06  
用途：交付研发 agent，指导后端、数据与部署能力落地

---

## 1. 文档目标

`plan_v0.1.1` 从“前端 API 交付清单”升级为“统一后端落地计划”，覆盖以下内容：

1. 后端 Java / Python 职责拆分与交互链路。
2. Garmin Connect 连接器实现（参考 `python-garminconnect`）。
3. raw 层数据表结构定义。
4. dbt 各层级 ETL 与表结构定义。
5. Docker Compose 全栈实现方案。
6. PostgreSQL 物理 schema 按功能域和数据层级拆分。

---

## 2. 范围与约束

### 2.1 本版本范围

1. Admin API（Java）能力完善。
2. Sync Worker（Python）能力完善。
3. 连接器抓取到 raw，再到 dbt 转换的全链路落地。
4. PostgreSQL + Redis + Compose 全栈联调闭环。

### 2.2 固定技术约束（继承 v1.0）

1. 后端：Java Spring Boot + Python Celery。
2. 数据层：raw + normalized + business-read/business-write。
3. 调度：Celery Beat + Worker。
4. 部署：Docker Compose。
5. 数据隔离：`account_id` 行级隔离。

---

## 3. 后端服务拆分与交互

## 3.1 Java（Admin API）职责

1. 用户认证与鉴权（JWT + RBAC）。
2. 连接器配置 CRUD。
3. 业务数据 CRUD（business-write 层）。
4. 手动同步任务下发。
5. 同步任务查询。
6. 审计日志记录与查询。
7. 数据归属校验（按 `account_id`）。

## 3.2 Python（Sync Worker + Scheduler）职责

1. 调用连接器适配层与第三方 API/SDK。
2. 执行定时同步、手动同步、补跑、重试。
3. 原始数据写入 raw 层。
4. 回写任务执行状态。
5. 不自动触发 dbt 转换执行。
6. 处理失败重试与限流退避。

### 3.3.1 PostgreSQL 物理 schema 约定

当前版本采用以下 schema 拆分：

1. `app`：事务与配置域（账号、用户、连接器配置、同步任务）。
2. `raw`：外部连接器原始落库层。
3. `staging`：dbt staging 视图层。
4. `intermediate`：dbt intermediate 视图层。
5. `marts`：dbt mart 表层。

## 3.3 Java 与 Python 交互（双轨）

采用“任务表 + Celery 队列”双轨：

1. Admin API 接收同步请求。
2. Admin API 写入 `sync_task`（`queued`）并记录上下文。
3. Admin API 投递 Celery 消息（`task_id`、`account_id`、`connector_config_id`）。
4. Worker 消费消息并更新状态为 `running`。
5. Worker 执行抓取并写 raw。
6. Worker 回写 `success/failed` 与统计字段。
7. Admin API 对外提供任务查询。
8. dbt 转换通过独立入口手动触发。

### 3.4 任务消息与状态契约

```ts
type SyncTaskMessage = {
  taskId: string;
  accountId: string;
  connectorConfigId: string;
  triggerType: "scheduled" | "manual" | "retry" | "backfill";
};

type SyncTaskRecord = {
  task_id: string;
  account_id: string;
  connector_config_id: string;
  trigger_type: "scheduled" | "manual" | "retry" | "backfill";
  status: "queued" | "running" | "success" | "failed";
  started_at?: string;
  finished_at?: string;
  fetched_count: number;
  written_count: number;
  skipped_count: number;
  error_message?: string;
};
```

---

## 4. Garmin Connect 连接器实现计划

## 4.1 接入策略

1. 基于 `python-garminconnect` 封装项目内 `GarminConnectorAdapter`。
2. 业务层禁止直接调用第三方库，统一走适配器。
3. 第三方异常统一映射为项目错误码。

## 4.2 认证与会话策略

1. 默认账号密码认证。
2. 兼容 MFA/challenge 分支。
3. `is_cn` 固定为 `true`（本版本强约束，不做前端配置项）。

示例（示意）：

```python
from garminconnect import Garmin

client = Garmin(email=username, password=password, is_cn=True)
client.login()
```

## 4.3 `is_cn` 固定规则

1. 所有 Garmin 连接器实例统一传 `is_cn=true`。
2. 配置模型不暴露 `is_cn` 字段。
3. 若后续需要国际区支持，在后续版本再引入区域配置。

## 4.4 错误映射

| 第三方异常类别 | 平台错误码 | 处理策略 |
|---|---|---|
| 认证失败 | `GARMIN_AUTH_FAILED` | 任务失败，不自动重试 |
| 网络错误 | `GARMIN_CONNECTION_ERROR` | 指数退避重试 |
| 限流 | `GARMIN_RATE_LIMITED` | 延迟重试并记录限流事件 |
| 未知异常 | `GARMIN_UNKNOWN_ERROR` | 任务失败并保留上下文 |

## 4.5 Garmin 当前已落地实体

1. 用户资料/概览（profile）。
2. 每日汇总（daily summary）。
3. 活动（activity）。
4. 睡眠（日级 sleep）。
5. 心率（日级 heart rate）。

说明：

1. `respiration`、`spo2`、`stress`、`body_composition`、`training_metrics` 仍延期到后续版本。
2. 当前数据库与 dbt 项目只保留已落地实体，不再为未实现实体保留空表或空模型占位。

---

## 5. raw 层表结构定义

## 5.1 设计原则

1. raw 记录必须可追溯来源与任务上下文。
2. 每张 raw 表必须有 `account_id`、`connector_config_id`、`sync_task_id`。
3. raw 表按 `domain + source delivery shape` 设计，不按连接器单独拆表。
4. 幂等键：`account_id + connector_config_id + source_stream + source_record_date + external_id`。
5. raw 仅保留元数据与 `payload_jsonb`，字段提取与实体拆分统一放在 dbt 中完成。

## 5.2 当前 v0.1.1 raw 表

### `raw.raw_sync_task_snapshot`

用于保存任务批次快照，支撑追溯与排障。

### 通用 health raw 表

当前版本对 `health` domain 统一收敛为以下 3 张通用 raw 表：

1. `raw.health_snapshot_record`
2. `raw.health_event_record`
3. `raw.health_timeseries_record`

说明：

1. 每张表统一携带 `connector_id`、`source_stream`、`external_id`、`source_record_date`、`source_record_at`、`source_updated_at`、`payload_hash`、`collected_at`、`payload_jsonb` 等元字段。
2. `snapshot_record` 用于 profile、daily summary、sleep 等“单次接口返回一个复合快照”的数据。
3. `event_record` 用于 activity 这类事件/会话数据。
4. `timeseries_record` 用于 heart rate 这类时序 payload。
5. `respiration`、`spo2`、`stress`、`body_composition`、`training_metrics` 等实体仍延后实现，但优先复用上述通用 raw 表，而不是新增连接器专属 raw 表。

## 5.4 多次同步去重与 raw 防膨胀策略

本策略同时适用于自动同步和手动同步，目标是在不牺牲当前值可用性的前提下控制 raw 层膨胀。

### 5.4.1 写入模式

1. 采用“仅 upsert”模式，不使用全量 append。
2. 相同幂等键在 raw 层始终保持单行当前值。
3. 历史追溯以任务表与任务统计为主，不在 raw 层保存同键多版本。

### 5.4.2 覆盖判定规则

1. 若 `source_updated_at` 存在：仅当新值更晚时覆盖。
2. 若 `source_updated_at` 缺失：仅当 `payload_hash` 变化时覆盖。
3. 两者都不满足时判定为重复数据，跳过更新。

### 5.4.3 实体粒度唯一键模板

1. 快照型接口：`source_stream + source_record_date + external_id`。
2. 时序型接口：`source_stream + source_record_date + external_id`，必要时将 `measure_ts` 编入 `external_id`。
3. 事件型接口：`source_stream + source_record_date + external_id`，活动类可直接使用 `activity_id`。
4. 无自然主键实体：`external_id = sha256(source_stream + source_record_date + stable_business_fields)`。

### 5.4.4 任务统计字段（用于评估重复同步）

`sync_task` 记录增加以下统计字段：

1. `fetched_count`
2. `inserted_count`
3. `updated_count`
4. `unchanged_count`
5. `deduped_count`

约束：

1. `fetched_count = inserted_count + updated_count + unchanged_count`
2. `deduped_count` 统计被判定为重复并跳过更新的记录。

### 5.4.5 参考 SQL（示意）

```sql
INSERT INTO raw_xxx (...)
VALUES (...)
ON CONFLICT (account_id, connector_config_id, entity_type, source_record_date, external_id)
DO UPDATE SET
  payload_jsonb = EXCLUDED.payload_jsonb,
  payload_hash = EXCLUDED.payload_hash,
  source_updated_at = EXCLUDED.source_updated_at,
  sync_task_id = EXCLUDED.sync_task_id,
  collected_at = EXCLUDED.collected_at,
  updated_at = NOW()
WHERE
  (
    EXCLUDED.source_updated_at IS NOT NULL
    AND (
      raw_xxx.source_updated_at IS NULL
      OR EXCLUDED.source_updated_at > raw_xxx.source_updated_at
    )
  )
  OR
  (
    EXCLUDED.source_updated_at IS NULL
    AND EXCLUDED.payload_hash <> raw_xxx.payload_hash
  );
```

### 5.4.6 保留策略

1. 当前版本按“当前值模型”执行，不保存同键历史版本。
2. 不做 TTL 清理（永久保留当前值）。
3. 若后续需要版本回放，再引入独立历史版本表。

---

## 6. dbt 分层 ETL 与模型定义

## 6.1 分层映射

1. `raw = dbt sources`
2. `normalized = staging + intermediate`
3. `business-read = marts`
4. `business-write = 应用维护表（不由 dbt 重建）`

## 6.2 dbt 目录结构

```text
models/
  staging/
    <source_system>/
  intermediate/
    <domain>/
  marts/
    <domain>/
snapshots/
macros/
tests/
docs/
```

## 6.3 命名规范

1. source: `src_<source_system>`
2. staging: `stg_<name>`
3. intermediate: `int_<name>`
4. dim: `dim_<name>`
5. fct: `fct_<name>`
6. mart: `mart_<name>`
7. snapshot: `snp_<name>`

## 6.4 Garmin 主题模型（v0.1.1）

### staging

1. `stg_garmin_profile`
2. `stg_garmin_daily_summary`
3. `stg_garmin_activity`
4. `stg_garmin_sleep`
5. `stg_garmin_heart_rate`

### intermediate

1. `int_health_daily_user`
2. `int_activity_enriched`
3. `int_sleep_enriched`
4. `int_heart_rate_daily`
5. `int_vitals_daily`

### marts（business-read）

1. `dim_user_health_profile`
2. `fct_health_daily_metrics`
3. `fct_activity_session`
4. `fct_sleep_session`
5. `mart_health_dashboard_daily`

## 6.5 materialization 规则

1. staging: `view`
2. intermediate: `view` 或 `table`
3. marts: `incremental table` 优先
4. 历史追踪：`snapshot`

## 6.6 数据测试与产物

最低必须包含：

1. `not_null + unique`
2. `relationships`
3. `accepted_values`
4. 关键业务一致性测试
5. source freshness

产物保留：

1. `manifest.json`
2. `catalog.json`
3. `run_results.json`
4. `sources.json`

---

## 7. Docker Compose 实现

## 7.1 服务清单（全栈 7 服务）

1. `frontend`
2. `admin-api`
3. `sync-worker`
4. `scheduler`
5. `dbt-runner`
6. `postgres`
7. `redis`

## 7.2 启动与依赖顺序

1. `postgres`、`redis` 先启动并健康。
2. `admin-api` 依赖 `postgres`。
3. `sync-worker`、`scheduler` 依赖 `postgres + redis`。
4. `dbt-runner` 依赖 `postgres`。
5. `frontend` 依赖 `admin-api`。

## 7.3 必备规则

1. 一键启动：`docker compose up -d`
2. 敏感配置通过环境变量注入。
3. PostgreSQL/Redis 使用 volume 持久化。
4. 核心服务必须配置 healthcheck。
5. 数据库迁移需在应用启动前执行。

## 7.4 关键环境变量（最小集）

| 服务 | 环境变量 |
|---|---|
| admin-api | `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `REDIS_URL` |
| sync-worker/scheduler | `DB_URL`, `REDIS_URL`, `CELERY_BROKER_URL`, `CELERY_RESULT_BACKEND` |
| dbt-runner | `DBT_PROFILES_DIR`, `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `DB_NAME` |
| frontend | `NUXT_PUBLIC_API_BASE` |

---

## 8. 对外接口与内部契约补充

## 8.1 强化接口

1. 保留现有认证与连接器配置接口。
2. 强化 `POST /api/v1/users/me/connectors/{connectorId}/sync-jobs`，必须返回 `jobId`。
3. 新增：`GET /api/v1/users/me/sync-jobs`。
4. 新增：`GET /api/v1/users/me/connectors/{connectorId}/sync-jobs/{jobId}`。
5. 与任务相关的接口响应中补充：`inserted_count`、`updated_count`、`unchanged_count`、`deduped_count`。

## 8.2 安全约束

1. 连接器凭据密文存储。
2. 日志与审计中禁止明文输出敏感字段。
3. 审计敏感字段仅记录“是否变更”与脱敏值。

---

## 9. 验收测试场景

## 9.1 后端交互

1. 手动同步任务状态可从 `queued -> running -> success/failed` 正确流转。
2. 同一 `account_id + connector_config_id` 禁止并发执行。
3. 失败重试、补跑链路可执行。

## 9.2 Garmin

1. `is_cn=true` 下认证成功可抓取数据。
2. MFA 分支可进入并正确处理。
3. 限流和网络异常可按策略处理。
4. 已通过真实 Garmin 账号验证 `activity / sleep / heart_rate` 的 payload 结构，并据此校正 worker 入库逻辑。

## 9.3 数据层

1. raw 层写入包含完整上下文字段，且 `payload_jsonb` 保留源接口原貌。
2. 同一源 payload 只在 raw 层落一次，sleep/body 等语义拆分在 dbt 中完成。
3. dbt 三层模型可运行。
4. 关键 marts 通过测试并产出 artifacts。

## 9.4 重复同步与去重

1. 同一时间窗重复触发自动+手动同步，raw 行数不应线性增长。
2. 相同数据重复到达时，`unchanged_count` 与 `deduped_count` 上升，`updated_count` 不应异常增长。
3. 当 `source_updated_at` 更晚时可覆盖旧值；更早时不得反向覆盖。
4. 当 `source_updated_at` 缺失且 `payload_hash` 不变时不得更新。

## 9.5 部署

1. Compose 7 服务可一键拉起。
2. 健康检查通过。
3. 前端可查询到 business-read 数据接口结果。

---

## 10. 风险与缓解

1. Garmin 接口行为可能因区域/风控变化不稳定。  
   缓解：固定 `is_cn=true` + 错误码映射 + 重试/退避。
2. 全量实体建模工作量大。  
   缓解：raw 全量先行，normalized/marts 分批产出并保证主链路优先。
3. 双服务协同复杂。  
   缓解：统一任务表状态机 + 消息契约 + 可观测性日志。
4. 仅保留当前值会降低同键历史版本回放能力。  
   缓解：当前阶段以防膨胀为优先，后续如需审计级回放再引入版本历史表。

---

## 11. 假设与默认值

1. v0.1.1 以本地 Compose 联调通过为里程碑。
2. Garmin 连接器默认中国区参数，`is_cn` 固定为 `true`。
3. 后续若扩展国际区，再引入区域配置与多连接器策略。
4. 业务查询优先消费 `business-read`，人工维护落 `business-write`。

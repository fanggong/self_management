# plan_v0.1.5 版本开发计划（Medical Report 前后端闭环）

版本：v0.1.5  
日期：2026-03-12  
用途：交付研发 agent，直接按本文档完成开发、联调与验收

---

## 1. 目标与结论

v0.1.5 目标是补齐 Medical Report 连接器的完整业务闭环：

1. 用户配置连接器（provider/modelId/apiKey）并测试可用性。  
2. 用户上传 PDF，后端调用模型解析并返回可编辑结构。  
3. 用户编辑确认后提交同步，系统生成 sync task 并异步写入 raw。  
4. `Connector > Tasks` 可检索该任务，筛选统计规则与 v0.1.3 保持一致。  

本版本锁定决策：

1. 交付范围：前后端同时交付。  
2. Parse 路径：Admin API 同步调用 Worker 内部 parse 接口。  
3. Parse session 存储：PostgreSQL（`app` schema），TTL 24 小时。  
4. Confirm Sync：异步入队执行（`queued -> running -> success/failed`）。  
5. Raw 落库：复用 `raw.health_snapshot_record`，`source_stream='medical_report'`。  
6. 时区口径：`Asia/Shanghai`；日期时间 `YYYY-MM-DD HH:MM:SS`；日期 `YYYY-MM-DD`。  

---

## 2. 当前基线与缺口（必须先统一认知）

当前代码现状（基于仓库事实）：

1. `admin-api` 的 connector 能力只支持 `garmin-connect`，`ConnectorService` 对 connectorId 做了 Garmin 单值校验。  
2. `frontend/pages/app.vue` 已有 Medical Report UI（上传、Parse、Confirm、section 编辑），但 Parse/Confirm 仍是本地 mock。  
3. `sync-worker/internal_api.py` 仅有 Garmin verify 内部接口。  
4. `sync-worker/services/sync_executor.py` 仅执行 Garmin 抓取与入 raw。  
5. 数据库中无 parse session 表，`app.sync_task` 也无 `parse_session_id` 字段。  

因此 v0.1.5 的核心是把已有前端交互替换为真实接口，并补齐后端解析与异步入库链路。

---

## 3. 总体架构与状态机

### 3.1 业务流程 A：Parse

1. 前端调用 `POST /api/v1/users/me/connectors/medical-report/parse`（multipart）。  
2. Admin API：
3. 校验 JWT、account 归属、连接器配置、文件类型/大小。  
4. 调 Worker 内部 `POST /internal/connectors/medical-report/parse`。  
5. 收到结构化结果后创建 parse session（`status=parsed`，`expires_at=now+24h`）。  
6. 返回 `parseSessionId + form + sections` 给前端。  

### 3.2 业务流程 B：Confirm Sync

1. 前端调用 `POST /api/v1/users/me/connectors/medical-report/sync-jobs`。  
2. Admin API：
3. 校验 `parseSessionId` 是否存在、归属当前账号、未过期。  
4. 将用户确认后的最终 payload 写入 parse session（`status=confirmed`）。  
5. 创建 `app.sync_task`（`trigger_type=manual`，`status=queued`，绑定 `parse_session_id`）。  
6. 返回任务初始视图（`jobId/status/...`）。  
7. scheduler/dispatcher 分发任务，Worker 消费后：
8. `running` -> 写 raw -> `success` 或 `failed`。  
9. 成功后更新 `connector_config.last_run_at`，`medical-report` 仍无 `next_run_at`。  

### 3.3 状态机定义

`app.medical_report_parse_session.status`：

1. `parsed`：已解析、待用户确认。  
2. `confirmed`：用户提交确认内容，任务已创建。  
3. `synced`：对应任务执行成功。  
4. `expired`：超过 TTL。  

`app.sync_task.status` 沿用现有：

1. `queued`  
2. `running`  
3. `success`  
4. `failed`

---

## 4. 对外 API 契约（字段级）

统一返回包装保持现状：

```ts
type ApiResult<T> = {
  success: boolean;
  data: T | null;
  message: string | null;
  code: string | null;
};
```

### 4.1 连接器接口增强

#### 4.1.1 `GET /api/v1/users/me/connectors`

`medical-report` 记录约束：

1. `schedule` 固定 `"-"`。  
2. `nextRun` 固定 `"-"`。  
3. `lastRun` 为最近成功同步时间，无则空字符串。  
4. `config` 包含：`provider`、`modelId`、`apiKey`（`apiKey` 脱敏返回）。  

#### 4.1.2 `PUT /api/v1/users/me/connectors/{connectorId}/configuration`

`connectorId=medical-report` 约束：

1. 必填：`provider`、`modelId`、`apiKey`。  
2. `provider` 枚举：`deepseek`、`volcengine`。  
3. `schedule` 入参可忽略，服务端统一写 `"-"`。  
4. `config` 仍走现有 `CryptoService` 加密存储，禁止明文落库。  

#### 4.1.3 `POST /api/v1/users/me/connectors/{connectorId}/connection-test`

`connectorId=medical-report` 行为：

1. 后端真实调用内部 verify 接口。  
2. 失败必须返回可识别错误码（认证失败/权限不足/模型不存在/网络错误）。  

---

### 4.2 新增：Parse 接口

#### 4.2.1 Endpoint

`POST /api/v1/users/me/connectors/medical-report/parse`

`Content-Type: multipart/form-data`

#### 4.2.2 Request 字段

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `recordNumber` | string | 是 | 非空，建议 <= 64 |
| `reportDate` | string | 是 | `YYYY-MM-DD` |
| `institution` | string | 是 | 非空，建议 <= 120 |
| `file` | file | 是 | PDF，默认上限 20MB |

后端校验规则：

1. 同时校验 MIME 与扩展名，不信任前端。  
2. `medical-report` 连接器未配置时拒绝解析。  
3. 当前账号只能使用自己的连接器配置。  

#### 4.2.3 Response

```ts
type MedicalReportParseResponse = {
  parseSessionId: string;
  connectorId: "medical-report";
  provider: "deepseek" | "volcengine";
  modelId: string;
  parsedAt: string; // YYYY-MM-DD HH:MM:SS, Asia/Shanghai
  form: {
    examiner: string;
    examDate: string; // YYYY-MM-DD
  };
  sections: Array<{
    sectionKey:
      | "general"
      | "internal_medicine"
      | "surgery"
      | "ophthalmology"
      | "ent"
      | "cbc"
      | "liver_function"
      | "kidney_function"
      | "ecg"
      | "imaging";
    items: Array<{
      itemKey: string;
      result: string;
      referenceValue: string;
      unit: string;
      abnormalFlag: string;
    }>;
  }>;
};
```

示例（简化）：

```json
{
  "success": true,
  "data": {
    "parseSessionId": "f8c2b2de-7bd6-4a46-a29d-1bc564913d31",
    "connectorId": "medical-report",
    "provider": "deepseek",
    "modelId": "deepseek-chat",
    "parsedAt": "2026-03-12 16:05:12",
    "form": {
      "examiner": "AI Parser",
      "examDate": "2026-03-10"
    },
    "sections": [
      {
        "sectionKey": "general",
        "items": [
          {
            "itemKey": "height",
            "result": "172",
            "referenceValue": "165-185",
            "unit": "cm",
            "abnormalFlag": ""
          }
        ]
      }
    ]
  },
  "message": "Medical report parsed successfully.",
  "code": null
}
```

---

### 4.3 新增：Confirm Sync 接口

#### 4.3.1 Endpoint

`POST /api/v1/users/me/connectors/medical-report/sync-jobs`

#### 4.3.2 Request

```ts
type MedicalReportSyncRequest = {
  parseSessionId: string;
  recordNumber: string;
  reportDate: string; // YYYY-MM-DD
  institution: string;
  fileName: string;
  form: {
    examiner: string;
    examDate: string; // YYYY-MM-DD
  };
  sections: Array<{
    sectionKey: string;
    items: Array<{
      itemKey: string;
      result: string;
      referenceValue: string;
      unit: string;
      abnormalFlag: string;
    }>;
  }>;
};
```

服务端规则：

1. `parseSessionId` 必须存在且归属于当前账号。  
2. 会话必须未过期。  
3. `reportDate`、`examDate` 格式必须合法。  
4. `sections` 允许为空数组，但结构必须合法。  

#### 4.3.3 Response

```ts
type MedicalReportSyncResponse = {
  jobId: string;
  connectorId: "medical-report";
  status: "queued" | "running" | "success" | "failed";
  triggerType: "manual";
  windowStart: string; // reportDate 00:00:00, Asia/Shanghai
  windowEnd: string;   // reportDate + 1day 00:00:00, Asia/Shanghai
  startedAt: string | null;
  finishedAt: string | null;
  fetchedCount: number;
  insertedCount: number;
  updatedCount: number;
  dedupedCount: number;
  errorMessage: string | null;
  createdAt: string;
};
```

行为要求：

1. 返回时任务至少已创建为 `queued`。  
2. 该任务必须可被 `GET /api/v1/users/me/sync-jobs` 检索。  
3. Worker 成功后更新 connector `lastRun`。  

---

### 4.4 任务列表接口约束（沿用 v0.1.3）

`GET /api/v1/users/me/sync-jobs` 继续支持：

1. `status`、`triggerType`、`domain`、`period`、`search`、分页、排序。  
2. `period`：`yesterday`、`last_7_days`、`last_30_days`。  
3. `facets.period`：`yesterday`、`last7Days`、`last30Days`。  
4. `status=failed` 才允许 `errorMessage` 非空。  
5. `medical-report` 任务必须纳入计数与过滤。

---

## 5. 内部接口契约（Admin API <-> Worker）

### 5.1 Verify

`POST /internal/connectors/medical-report/verify`

Request：

```json
{
  "config": {
    "provider": "deepseek",
    "modelId": "deepseek-chat",
    "apiKey": "sk-***"
  }
}
```

Response：

```json
{
  "success": true,
  "code": "CONNECTOR_VERIFIED",
  "message": "Medical Report provider credentials verified successfully."
}
```

### 5.2 Parse

`POST /internal/connectors/medical-report/parse`

Request（建议 JSON + base64 文件内容，避免 multipart 内部复杂度）：

```ts
type InternalMedicalParseRequest = {
  accountId: string;
  connectorConfigId: string;
  provider: "deepseek" | "volcengine";
  modelId: string;
  apiKey: string;
  recordNumber: string;
  reportDate: string; // YYYY-MM-DD
  institution: string;
  fileName: string;
  fileBase64: string;
};
```

Response：

1. 成功：返回 `form + sections`。  
2. 失败：返回标准 `success=false/code/message`，Admin API 负责映射成对外错误码。

---

## 6. 数据层详细设计

## 6.1 Migration 计划

建议新增迁移：`V8__medical_report_parse_session.sql`

包含以下变更：

1. 新建 `app.medical_report_parse_session`。  
2. `app.sync_task` 新增 `parse_session_id UUID NULL REFERENCES app.medical_report_parse_session(id)`。  
3. 为查询和清理创建索引。  
4. 补齐 `medical-report` seed connector（若不存在则插入）。  

## 6.2 表结构建议

### 6.2.1 `app.medical_report_parse_session`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | UUID PK | parseSessionId |
| `account_id` | UUID FK | 账户隔离 |
| `connector_config_id` | UUID FK | 对应连接器配置 |
| `provider` | VARCHAR(32) | deepseek/volcengine |
| `model_id` | VARCHAR(128) | 模型标识 |
| `record_number` | VARCHAR(64) | 编号 |
| `report_date` | DATE | 报告日期 |
| `institution` | VARCHAR(120) | 医疗机构 |
| `file_name` | VARCHAR(255) | 上传文件名 |
| `parsed_payload_jsonb` | JSONB | parse 原始结构化结果 |
| `confirmed_payload_jsonb` | JSONB | 用户确认后的最终结果 |
| `status` | VARCHAR(32) | parsed/confirmed/synced/expired |
| `expires_at` | TIMESTAMPTZ | 过期时间 |
| `created_at` | TIMESTAMPTZ | 创建时间 |
| `updated_at` | TIMESTAMPTZ | 更新时间 |

索引：

1. `(account_id, status, expires_at)`  
2. `(account_id, report_date, created_at desc)`  
3. `(connector_config_id, created_at desc)`  

### 6.2.2 `app.sync_task` 新增字段

1. `parse_session_id UUID NULL`  
2. 用于 Worker 执行时检索确认 payload。  

## 6.3 Raw 写入规范

目标表：`raw.health_snapshot_record`

固定字段：

1. `connector_id = 'medical-report'`  
2. `source_stream = 'medical_report'`  
3. `source_record_date = reportDate`  
4. `source_record_at = reportDate 00:00:00 +08`  
5. `source_updated_at = NULL`  
6. `payload_jsonb = confirmed_payload_jsonb`  
7. `external_id = sha256(lower(recordNumber)+'|'+reportDate+'|'+lower(trim(institution)))`  

去重策略沿用现有 upsert 逻辑：按唯一键冲突，hash 相同则 `unchanged`，否则 `updated`。

---

## 7. 开发任务拆分（按执行顺序）

### Phase 1：数据库与实体层

1. 新增 `V8` 迁移（parse session + sync_task 字段 + seed）。  
2. Admin API 新增 parse session Entity/Repository。  
3. `SyncTaskEntity` 增加 `parseSessionId` 字段。  
4. DoD：Flyway 通过，服务可启动。  

### Phase 2：Admin API 接口层

1. 扩展 `ConnectorService`：
2. `medical-report` catalog/config/test 分支。  
3. `saveConfiguration` 对 medical-report 强制 `schedule='-'`。  
4. 扩展 `ConnectorVerificationClient`：
5. 保留 Garmin verify。  
6. 新增 medical-report verify/parse 调用方法。  
7. 新增 `MedicalReportController` 与 `MedicalReportService`：
8. 实现 parse、confirm sync。  
9. DoD：Swagger/接口可调用，错误码符合规范。  

### Phase 3：Worker 能力

1. 新增 `connectors/medical_report.py` 适配层。  
2. `internal_api.py` 增加 medical-report verify/parse endpoint。  
3. `db.py` 增加 parse session 读取与状态更新方法。  
4. `sync_executor.py` 增加 medical-report 任务分支。  
5. DoD：手动触发任务可成功写 raw。  

### Phase 4：前端接入

1. `frontend/types/connectors.ts` 增加 parse/sync DTO。  
2. `frontend/services/api/connectors.ts` 增加 parse/sync 方法（HTTP + mock 模式兼容）。  
3. `frontend/pages/app.vue`：
4. Parse 按钮改调用真实 parse。  
5. Confirm 按钮改调用真实 sync-jobs。  
6. 去除 Medical Report 本地 mock raw 计数提示，替换为真实 job message。  
7. DoD：页面全流程可跑通且不依赖本地模拟延时。  

### Phase 5：联调与回归

1. 验证 `sync-jobs` 对 medical-report 的检索、筛选、facets。  
2. 验证 Garmin 现有流程不回归。  
3. 补充 `scripts/dev/verify_v0_1_5.sh`。  
4. DoD：验收清单全部通过。  

---

## 8. 错误码与映射

| 场景 | HTTP | code | message |
|---|---|---|---|
| 上传非 PDF | 400 | INVALID_FILE_TYPE | Only PDF files are supported. |
| 文件超限 | 400 | FILE_TOO_LARGE | File size exceeds limit. |
| 连接器未配置 | 400 | CONNECTOR_NOT_CONFIGURED | Please configure provider/model/api key first. |
| 模型认证失败 | 400 | MODEL_AUTH_FAILED | Provider authentication failed. |
| 模型不可达 | 502 | MODEL_CONNECTION_ERROR | Unable to reach model provider right now. |
| 解析失败 | 422 | REPORT_PARSE_FAILED | Unable to parse report content. |
| parseSession 无效/过期 | 400 | INVALID_PARSE_SESSION | Parse session is invalid or expired. |
| 参数校验失败 | 400 | VALIDATION_ERROR | Invalid request payload. |
| 未登录 | 401 | UNAUTHORIZED | Authentication required. |
| 越权访问 | 403 | FORBIDDEN | Access denied. |

---

## 9. 验收用例（必须逐项执行）

### 9.1 API 验收

1. medical-report 配置保存成功，`GET connectors` 返回 `schedule='-'`、`nextRun='-'`。  
2. connection-test 对正确/错误凭据分别返回成功和明确错误码。  
3. parse 上传合法 PDF 可返回结构化结果。  
4. parse 上传非法文件返回 `INVALID_FILE_TYPE`。  
5. confirm sync 成功返回 queued 任务。  
6. 过期 session confirm 返回 `INVALID_PARSE_SESSION`。  

### 9.2 任务链路验收

1. queued 任务可被 dispatcher 分发。  
2. Worker 执行后状态正确流转。  
3. `GET /sync-jobs` 可检索 medical-report 任务。  
4. period/filter/facets 与 v0.1.3 规则一致。  

### 9.3 数据层验收

1. `raw.health_snapshot_record` 有 `connector_id='medical-report'` 数据。  
2. 同一 `recordNumber+reportDate+institution` 重复提交不会线性增行。  
3. 统计字段 `inserted/updated/deduped` 与实际写入行为一致。  

### 9.4 前端验收

1. 配置 -> 测试连接 -> 上传解析 -> 编辑 -> Confirm 全流程可用。  
2. 成功后 Tasks 页可见新任务并可筛选。  
3. 错误码提示与后端返回一致。  

---

## 10. 非目标与默认假设

1. 本版本不实现 medical-report 的自动调度。  
2. 本版本不实现 medical-report dbt 主题模型（仅 raw 落地）。  
3. 本版本不做 PDF 文件长期对象存储。  
4. parse session TTL 固定 24 小时，过期通过后台清理任务处理。  
5. 本版本不改现有 Garmin `is_cn=true` 策略。  

---

## 11. 交付物清单

1. Admin API：
2. medical-report parse/sync 对外接口。  
3. medical-report connection-test 增强。  
4. parse session 数据模型与服务。  

5. Sync Worker：
6. medical-report verify/parse 内部接口。  
7. medical-report sync 执行分支。  

8. 数据库：
9. `V8` 迁移脚本。  

10. 前端：
11. Medical Report 流程接入真实 API。  

12. 验证：
13. `scripts/dev/verify_v0_1_5.sh`。  
14. 验收记录（接口、链路、数据层、前端）。

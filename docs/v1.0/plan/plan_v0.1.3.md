# plan_v0.1.3 当前版本新增 API 交付清单

版本：v0.1.3  
日期：2026-03-10  
用途：交付后端研发，补齐当前版本相对 `plan_v0.1.1.md` 新增的接口能力

---

## 1. 文档目标

`plan_v0.1.3` 不重复定义 v0.1.1 已经明确的认证、用户设置、连接器配置、连接器状态切换、手动同步创建等接口。  
本文件只整理当前版本新增的前端页面能力所需要的 API 增量，供后端研发直接实现。

当前增量页面只有一个：

1. `Connector > Tasks`（当前前端为 Mock 页面，后续切换真实接口）

---

## 2. 本版本新增范围

## 2.1 新增页面能力

当前 `Connector > Tasks` 页面已经落地以下前端交互：

1. `All Tasks`
2. `Status`：`Pending`、`In Progress`、`Completed`、`Failed`
3. `Trigger Type`：`Manual`、`Scheduled`
4. `Domain`：`Health`、`Finance`
5. 顶部搜索框：按连接器名称模糊搜索
6. 右侧任务列表：以 card/list 形式展示任务摘要

## 2.2 与 v0.1.1 的关系

v0.1.1 已定义并已实现以下任务相关接口：

1. `POST /api/v1/users/me/connectors/{connectorId}/sync-jobs`
2. `GET /api/v1/users/me/sync-jobs`
3. `GET /api/v1/users/me/connectors/{connectorId}/sync-jobs/{jobId}`

但是当前 `GET /api/v1/users/me/sync-jobs` 仅能返回简单列表，不足以支撑 v0.1.3 页面，主要缺口如下：

1. 不支持 `status / triggerType / domain / search` 查询。
2. 不支持分页。
3. 不返回页面所需的 facet count。
4. 不返回 `connectorName` 与 `domain`。
5. 当前返回结构仍偏底层，不适合直接驱动任务筛选页。

因此，v0.1.3 的核心工作是：

1. 强化 `GET /api/v1/users/me/sync-jobs`。
2. 保持 `POST /sync-jobs` 与 `GET /connectors/{connectorId}/sync-jobs/{jobId}` 不变。

---

## 3. API 增量总览

| 类型 | 路径 | 状态 | 说明 |
|---|---|---|---|
| Enhanced | `GET /api/v1/users/me/sync-jobs` | 必须实现 | 支撑 `Connector > Tasks` 页的搜索、筛选、统计与分页 |
| No Change | `POST /api/v1/users/me/connectors/{connectorId}/sync-jobs` | 维持 v0.1.1 | 当前页面无新增要求 |
| No Change | `GET /api/v1/users/me/connectors/{connectorId}/sync-jobs/{jobId}` | 维持 v0.1.1 | 当前页面暂未使用详情接口 |

---

## 4. 强化接口：同步任务列表查询

## 4.1 接口定义

`GET /api/v1/users/me/sync-jobs`

用途：

1. 查询当前登录用户的同步任务列表。
2. 驱动 `Connector > Tasks` 页面左侧筛选计数。
3. 驱动右侧任务 card/list。

鉴权：

1. Bearer JWT。
2. 仅返回当前 `account_id` 下的任务。

---

## 4.2 Query 参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `page` | integer | 否 | `1` | 页码，最小为 `1` |
| `pageSize` | integer | 否 | `20` | 每页条数，建议最大 `100` |
| `search` | string | 否 | `""` | 按连接器名称模糊搜索，大小写不敏感 |
| `status` | enum | 否 | - | `queued` / `running` / `success` / `failed` |
| `triggerType` | enum | 否 | - | `manual` / `scheduled` |
| `domain` | enum | 否 | - | `health` / `finance` |
| `sortBy` | enum | 否 | `createdAt` | `createdAt` / `windowStart` / `windowEnd` / `startedAt` / `finishedAt` |
| `sortOrder` | enum | 否 | `desc` | `asc` / `desc` |

说明：

1. `search` 仅用于连接器名称搜索，不用于任务 ID、错误信息搜索。
2. 本版本前端筛选只支持 `manual`、`scheduled`；`retry`、`backfill` 暂不对前端暴露。
3. 若当前账号下尚无 `finance` 任务，`domain=finance` 仍应允许查询，并返回空列表与计数 `0`。

---

## 4.3 返回结构

统一仍使用现有包装：

```ts
type ApiResult<T> = {
  success: boolean;
  data?: T;
  message?: string;
  code?: string;
};
```

`data` 建议升级为分页对象，而不是简单数组：

说明：

1. 当前前端 `Connector > Tasks` 仍然使用 Mock 数据，尚未接入真实 `GET /api/v1/users/me/sync-jobs`。
2. 因此本版本允许直接把该接口从“数组返回”升级为“分页对象返回”，不需要为了兼容旧页面继续维持旧结构。

```ts
type SyncJobListResponse = {
  items: SyncJobListItem[];
  page: {
    page: number;
    pageSize: number;
    total: number;
    totalPages: number;
  };
  facets: SyncJobFacets;
};

type SyncJobListItem = {
  jobId: string;
  connectorId: string;
  connectorName: string;
  domain: "health" | "finance";
  status: "queued" | "running" | "success" | "failed";
  triggerType: "manual" | "scheduled";
  windowStart: string | null;
  windowEnd: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  fetchedCount: number;
  insertedCount: number;
  updatedCount: number;
  dedupedCount: number;
  errorMessage: string | null;
  createdAt: string;
};

type SyncJobFacets = {
  allTasks: number;
  status: {
    queued: number;
    running: number;
    success: number;
    failed: number;
  };
  triggerType: {
    manual: number;
    scheduled: number;
  };
  domain: {
    health: number;
    finance: number;
  };
};
```

说明：

1. `connectorName` 是当前页面必需字段，不能只返回 `connectorId`。
2. `domain` 是当前页面必需字段，用于右侧卡片展示和左侧 `Domain` 筛选。
3. `unchangedCount` 当前页面不展示，本版本列表接口可不返回。
4. `errorMessage` 仅在 `status=failed` 时需要非空；其他状态建议返回 `null`。

---

## 4.4 Facet 计数规则

左侧筛选区不是简单的“整表全量计数”，而是带上下文的 facet count。  
后端需要按以下规则返回 `facets`：

### 4.4.1 `allTasks`

语义：

1. 应用 `search`
2. 忽略 `status / triggerType / domain`

即：

1. 顶部搜索输入 `Garmin` 后，`All Tasks` 应只统计名称命中 `Garmin` 的任务总数。

### 4.4.2 `status`

语义：

1. 应用当前 `search`
2. 应用当前 `triggerType`
3. 应用当前 `domain`
4. 忽略当前 `status`

目的：

1. 用户选中 `Domain=Health` 后，`Pending / In Progress / Completed / Failed` 计数应变为“Health 域下各状态分布”。

### 4.4.3 `triggerType`

语义：

1. 应用当前 `search`
2. 应用当前 `status`
3. 应用当前 `domain`
4. 忽略当前 `triggerType`

### 4.4.4 `domain`

语义：

1. 应用当前 `search`
2. 应用当前 `status`
3. 应用当前 `triggerType`
4. 忽略当前 `domain`

该规则需要与当前前端 Mock 页行为保持一致，否则左侧筛选数字会与右侧结果脱节。

---

## 4.5 返回示例

请求：

```http
GET /api/v1/users/me/sync-jobs?page=1&pageSize=20&search=garmin&status=success&domain=health
Authorization: Bearer <token>
```

响应示意：

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "jobId": "ed0bd727-9af6-4c84-8f40-511d7eecb67b",
        "connectorId": "garmin-connect",
        "connectorName": "Garmin Connect",
        "domain": "health",
        "status": "success",
        "triggerType": "scheduled",
        "windowStart": "2026-03-02 02:00:00",
        "windowEnd": "2026-03-05 02:00:00",
        "startedAt": "2026-03-05 02:00:02",
        "finishedAt": "2026-03-05 02:01:11",
        "fetchedCount": 145,
        "insertedCount": 103,
        "updatedCount": 17,
        "dedupedCount": 25,
        "errorMessage": null,
        "createdAt": "2026-03-05 02:00:00"
      }
    ],
    "page": {
      "page": 1,
      "pageSize": 20,
      "total": 3,
      "totalPages": 1
    },
    "facets": {
      "allTasks": 6,
      "status": {
        "queued": 0,
        "running": 0,
        "success": 3,
        "failed": 1
      },
      "triggerType": {
        "manual": 1,
        "scheduled": 2
      },
      "domain": {
        "health": 3,
        "finance": 0
      }
    }
  }
}
```

---

## 5. 字段口径与展示约束

## 5.1 时间字段

以下字段统一按 `Asia/Shanghai` 格式化为：

`YYYY-MM-DD HH:MM:SS`

涉及字段：

1. `windowStart`
2. `windowEnd`
3. `startedAt`
4. `finishedAt`
5. `createdAt`

约束：

1. 数据库存储必须统一使用 `Asia/Shanghai` 时区语义，不使用 `UTC` 作为本业务口径。
2. 前端展示也必须统一使用 `Asia/Shanghai`，禁止按浏览器本地时区自动偏移。
3. 若字段为空，返回 `null`，不要返回 `"-"`。

## 5.2 状态字段

数据库状态与页面文案映射：

| API 值 | 页面文案 |
|---|---|
| `queued` | `Pending` |
| `running` | `In Progress` |
| `success` | `Completed` |
| `failed` | `Failed` |

## 5.3 Trigger Type 字段

本版本只需要：

1. `manual`
2. `scheduled`

若内部未来引入：

1. `retry`
2. `backfill`

建议在后续版本再对前端开放，本版后端接口先不要返回。

## 5.4 错误信息字段

规则：

1. `status=failed`：`errorMessage` 返回真实错误文本。
2. 其他状态：`errorMessage = null`。

原因：

1. 当前前端只在失败任务上展示错误信息，不希望出现 `No errors` 一类占位文案。

---

## 6. 实现建议（后端）

## 6.1 查询来源

基础表：

1. `app.sync_task`
2. `app.connector_config`

推荐做法：

1. `sync_task.connector_config_id -> connector_config.id` 关联。
2. `connectorId` 取 `connector_config.connector_id`。
3. `domain` 取 `connector_config.category`。
4. `connectorName` 由服务层按 `connectorId` 做 catalog 映射生成。

## 6.2 排序默认值

默认建议：

1. `sortBy = createdAt`
2. `sortOrder = desc`

理由：

1. 当前页面是任务列表页，用户最先关心的是最近任务。

## 6.3 分页建议

1. 默认 `pageSize = 20`
2. 最大 `pageSize = 100`
3. 超出范围直接返回 `VALIDATION_ERROR`

## 6.4 查询性能建议

建议索引：

1. `app.sync_task (account_id, created_at desc)`
2. `app.sync_task (account_id, status, created_at desc)`
3. `app.sync_task (account_id, trigger_type, created_at desc)`
4. `app.sync_task (account_id, connector_config_id, created_at desc)`
5. `app.connector_config (account_id, category, connector_id)`

若后续搜索量增大：

1. 可考虑对 `connectorName` 映射后做额外搜索优化。
2. 当前版本先允许通过 `connector_config.connector_id` + 服务层名称映射满足搜索。

---

## 7. 错误码建议

| 场景 | HTTP | code | message |
|---|---|---|---|
| `page < 1` 或 `pageSize <= 0` | `400` | `VALIDATION_ERROR` | Invalid pagination parameters. |
| 非法 `status / triggerType / domain / sortBy / sortOrder` | `400` | `VALIDATION_ERROR` | Invalid query parameter. |
| 未登录 | `401` | `UNAUTHORIZED` | Authentication required. |
| Token 无效 | `401` | `UNAUTHORIZED` | Authentication required. |

说明：

1. 列表查询为空时应返回 `200` + 空数组，而不是 `404`。

---

## 8. 验收清单

后端交付完成后，应满足以下条件：

1. `Connector > Tasks` 可用真实接口替换当前 Mock 数据。
2. 任务列表可按 `Status / Trigger Type / Domain / Search` 组合筛选。
3. 左侧各筛选项计数与当前搜索/筛选上下文一致。
4. 任务卡片可直接展示 `Job ID / Connector / Status / Trigger Type / Window Start / Window End / Started At / Finished At / Fetched Count / Inserted Count / Updated Count / Deduped Count / Error Message（仅失败时）`。
5. 时间字段全部为 `Asia/Shanghai` 的 `YYYY-MM-DD HH:MM:SS`。

---

## 9. 本版本结论

相对 `plan_v0.1.1.md`，v0.1.3 真正需要后端补齐的新增能力只有一块：

1. 将 `GET /api/v1/users/me/sync-jobs` 从“简单列表接口”升级为“可搜索、可筛选、可分页、可返回 facet count 的任务查询接口”。

其余接口：

1. 继续沿用 v0.1.1 定义。
2. 当前无需新增。

---

## 10. v0.1.3 完整实施计划（Execution Plan）

## 10.1 Summary

1. 交付范围采用端到端：后端任务查询接口增强 + 前端 `Connector > Tasks` 从 Mock 切换为真实接口 + 联调验收一次完成。
2. `GET /api/v1/users/me/sync-jobs` 直接升级为新结构（`items/page/facets`），不保留旧数组返回。
3. 时区口径统一为 `Asia/Shanghai`，历史数据不做值平移，仅做口径与配置统一。
4. 分页交互采用“加载更多”，不做传统页码翻页。

## 10.2 Implementation Changes

### A. 后端接口与查询能力

1. 强化 `GET /api/v1/users/me/sync-jobs`，支持参数：
2. `page`、`pageSize`、`search`、`status`、`triggerType`、`domain`、`sortBy`、`sortOrder`。
3. 返回结构固定为 `ApiResult<SyncJobListResponse>`，其中 `SyncJobListResponse` 必含 `items`、`page`、`facets`。
4. `items` 字段固定输出：`jobId`、`connectorId`、`connectorName`、`domain`、`status`、`triggerType`、`windowStart`、`windowEnd`、`startedAt`、`finishedAt`、`fetchedCount`、`insertedCount`、`updatedCount`、`dedupedCount`、`errorMessage`、`createdAt`。
5. `errorMessage` 规则固定：`failed` 返回真实错误；非 `failed` 返回 `null`。
6. `triggerType` 对外仅允许 `manual`、`scheduled`；`retry`、`backfill` 本版不暴露。
7. 保持以下接口不变：
8. `POST /users/me/connectors/{connectorId}/sync-jobs`
9. `GET /users/me/connectors/{connectorId}/sync-jobs/{jobId}`

### B. 后端筛选计数（facets）实现

1. `allTasks` 计数规则：仅应用 `search`，忽略 `status/triggerType/domain`。
2. `status` 计数规则：应用 `search + triggerType + domain`，忽略当前 `status`。
3. `triggerType` 计数规则：应用 `search + status + domain`，忽略当前 `triggerType`。
4. `domain` 计数规则：应用 `search + status + triggerType`，忽略当前 `domain`。
5. `domain` 需稳定返回 `health`、`finance` 两个键，即使值为 `0`。

### C. 后端数据与时区约束

1. 任务列表查询统一从 `app.sync_task` 关联 `app.connector_config` 获取 `connectorId` 与 `domain`。
2. `connectorName` 由服务层按 `connectorId` 做目录映射输出。
3. 所有时间字段输出格式固定为 `YYYY-MM-DD HH:MM:SS`，时区固定 `Asia/Shanghai`。
4. 数据库存储口径统一为 `Asia/Shanghai`，本版不做历史时间值平移，只做配置和读写口径统一。
5. 新增索引以支撑筛选和排序：
6. `app.sync_task (account_id, created_at desc)`
7. `app.sync_task (account_id, status, created_at desc)`
8. `app.sync_task (account_id, trigger_type, created_at desc)`
9. `app.sync_task (account_id, connector_config_id, created_at desc)`
10. `app.connector_config (account_id, category, connector_id)`

### D. 前端接入与交互切换

1. 移除 `Connector > Tasks` 本地 mock 任务源，改为调用真实 `GET /users/me/sync-jobs`。
2. 左侧筛选和顶部搜索直接驱动请求参数，计数改为使用服务端 `facets`，不再前端本地重算。
3. 右侧任务卡片渲染改为使用 `items` 字段，保持现有视觉结构和状态映射不变。
4. 分页采用“加载更多”：首次请求 `page=1`，点击加载更多时 `page+1` 追加到列表。
5. 搜索输入使用防抖触发请求，切换任一筛选条件后重置到第 1 页并清空已追加列表后重拉。
6. 加入加载态、空态、错误态与重试按钮，确保接口异常时页面可恢复。

### E. 实施节奏

1. 阶段一：后端接口与查询逻辑完成并通过接口测试。
2. 阶段二：前端切换真实接口并完成筛选/搜索/加载更多联动。
3. 阶段三：端到端联调与回归，输出 v0.1.3 验收记录并更新计划文档。

## 10.3 Test Plan

### A. 后端契约测试

1. `GET /users/me/sync-jobs` 在无参数、单参数、组合参数场景均返回新结构。
2. `status/triggerType/domain/sortBy/sortOrder/page/pageSize` 非法值返回 `400 + VALIDATION_ERROR`。
3. 未登录或 token 无效返回 `401 + UNAUTHORIZED`。
4. 空结果返回 `200`，`items=[]`，`facets` 各项计数正确。

### B. 后端语义测试

1. `facets` 计数严格符合四条上下文规则（含 `search` 影响）。
2. 非 `failed` 任务的 `errorMessage` 为 `null`。
3. 时间字段输出均为 `Asia/Shanghai` 的 `YYYY-MM-DD HH:MM:SS`。
4. 任务列表默认排序为 `createdAt desc`。

### C. 前端联调测试

1. `All Tasks / Status / Trigger Type / Domain / Search` 组合筛选结果与计数一致。
2. “加载更多”可连续追加数据，筛选或搜索变化后可正确重置并重新加载。
3. 失败任务显示错误信息，非失败任务不显示错误信息区。
4. 页面在无数据、有数据、接口失败三种场景均可正常交互。

## 10.4 Assumptions & Defaults

1. 本计划以 `docs/v1.0/plan/plan_v0.1.3.md` 为唯一需求基线。
2. 本版接口返回结构允许破坏性升级为 `items/page/facets`（无旧结构兼容要求）。
3. 历史数据不做 `+8h` 值平移；“迁移”仅指时区口径与读写策略统一到 `Asia/Shanghai`。
4. 本版分页体验固定为“加载更多”，不实现页码跳转 UI。
5. 本版对外 `triggerType` 固定 `manual/scheduled`，其余类型延后。

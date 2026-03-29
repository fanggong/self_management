# Medical Report 连接器规范

## 目的
- 定义 `medical-report` 连接器的配置、解析、确认同步、原始落库和下游模型规则。
- 保证任何涉及体检报告或医疗报告导入的任务都采用统一流程。

## 适用范围
- 适用于 Medical Report 连接器的前端配置、报告上传、解析会话、后端同步、raw 落库和 dbt 模型任务。

## 规范正文

### 连接器基本定义
- `connector_id` 必须固定为 `medical-report`。
- 连接器分类必须归入健康数据域。
- 接入模式必须定义为 `manual parse/confirm`。
- 该连接器不得被视为标准自动调度型连接器。

### 配置字段规范
- Medical Report 的最小配置字段必须为：
  - `provider`
  - `modelId`
  - `apiKey`
- `provider` 必须来自受支持的提供方集合，不得接受任意自由文本。
- `modelId` 必须精确表达所使用的模型。
- `apiKey` 必须作为敏感字段处理，并以密文存储。

### 验证规范
- 保存配置前必须完成以下验证：
  - `provider` 合法
  - `modelId` 非空
  - `apiKey` 满足最小可用长度
  - 上游模型服务可访问且凭证可用
- 验证必须针对实际提供方能力，不得只验证字段存在而跳过远程连通性。

### 解析流程规范
- 该连接器的业务流程必须拆为两个阶段：
  1. `parse`
  2. `confirm sync`
- `parse` 阶段只负责读取输入、调用模型、生成结构化草稿，不得直接写入最终原始事实。
- `confirm sync` 阶段必须以用户确认后的结构化结果作为写入依据。
- 解析输入必须至少包含：
  - `recordNumber`
  - `reportDate`
  - `institution`
  - 报告文件

### 文件与输入规范
- 报告文件必须限制为 PDF 或专属文档允许的格式。
- 报告文件必须经过基础格式校验，不得把任意二进制内容送入解析链路。
- `reportDate` 必须使用稳定日期格式。
- 缺失关键元数据的解析请求必须直接失败，不得生成半成品会话。

### 解析会话规范
- 每次解析都必须生成独立的解析会话。
- 解析会话必须具备：
  - 会话标识
  - 所属账户
  - 所属连接器实例
  - 解析结果
  - 状态
  - 过期时间
- 解析会话必须是短期有效且单次消费的，不得被长期复用为事实来源。
- 解析后的字段允许用户在确认前修订，但修订后的结构必须仍然满足统一 section/item 规范。

### 确认同步规范
- 只有处于可用状态且未过期的解析会话，才允许进入确认同步。
- 确认同步必须生成正式同步任务记录。
- 确认同步成功后，解析会话必须标记为已消费，不得重复使用。
- 解析成功不等于同步成功，文案、任务和状态都必须区分这两个阶段。

### 原始落库规范
- 原始落库必须按单份报告快照保存。
- 原始载荷中必须保留：
  - `parseSessionId`
  - `provider`
  - `modelId`
  - `recordNumber`
  - `reportDate`
  - `institution`
  - `fileName`
  - `sections`
- `sections` 结构必须稳定包含：
  - `sectionKey`
  - `examiner`
  - `examDate`
  - `items`
- `items` 结构必须稳定包含：
  - `itemKey`
  - `result`
  - `referenceValue`
  - `unit`
  - `abnormalFlag`

### 下游 dbt 规范
- Medical Report 必须至少具备以下 staging 模型：
  - 报告级快照模型
  - 报告明细行模型
- 模型命名必须以 `stg_medical_report_` 开头。
- 必须具备对应的 intermediate 健康域模型，命名必须以 `int_health_medical_report_` 开头。
- 即使尚未进入 mart，也不得跳过 staging 与 intermediate。

### 任务与状态规范
- 该连接器的同步任务必须是手动触发型任务。
- 自动调度、周期拉取和时间窗批量同步都不属于该连接器的默认能力。
- 任务成功必须代表原始报告事实已经写入成功。
- 失败任务必须记录业务错误或系统错误，供用户追踪。

### 安全与限制规范
- 上传文件和模型凭证都属于敏感输入，必须最小暴露。
- 原始报告内容不得写入不受控日志。
- 用户修订的结构化结果必须只在确认同步阶段写入原始层，不得在解析阶段提前持久化为正式事实。

## 边界与例外
- 该连接器不适用 cron 调度和批量抓取窗口策略。
- 若未来支持新的模型提供方，必须先扩展本文件中的 `provider` 规范，再实现代码。
- 若未来引入 marts，仍必须保持报告级与明细级 staging/intermediate 结构不被绕过。

## 实施清单
- 先定义并校验 `provider/modelId/apiKey`。
- 再实现 PDF 解析输入与结构化输出。
- 再实现解析会话、过期和单次消费规则。
- 再实现确认同步与 raw 快照写入。
- 最后补齐 staging/intermediate、测试和专属文档。

## 常见检索词
- `medical-report`
- `provider modelId apiKey`
- `parseSession`
- `manual parse confirm`
- `sections items`
- `stg_medical_report`
- `int_health_medical_report`

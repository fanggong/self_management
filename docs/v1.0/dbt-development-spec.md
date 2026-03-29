# dbt 开发规范

## 目的
- 定义 dbt 的分层职责、命名规范、文档标准、测试要求和模型变更流程。
- 保证原始接入数据向消费模型的演进路径稳定、可追踪、可验证。

## 适用范围
- 适用于 `dbt` 子工程中的 `models`、`sources`、`macros`、`tests`、`seeds` 和 runner 相关改动。
- 适用于新增模型、字段扩展、血缘修复、测试补齐、marts 建设和数据回归修复。

## 规范正文

### 分层职责
- `raw`
  不是 dbt 模型层，而是 dbt `sources` 的来源层。
- `staging`
  负责把 `raw` 中的原始载荷解析为结构化字段，并保留必要血缘信息。
- `intermediate`
  负责在不面向最终消费的前提下，做语义整理、清洗、统一和组合。
- `marts`
  负责向看板、查询和业务消费暴露稳定模型。
- 新字段和新逻辑必须先判断属于哪一层，不得跨层跳写。

### staging 规范
- staging 模型命名必须以 `stg_` 开头。
- staging 模型必须显式绑定来源连接器或来源流语义。
- staging 必须保留原始血缘与采集字段，至少包括适用项：
  - `account_id`
  - `connector_config_id`
  - `sync_task_id`
  - `connector_id`
  - `source_stream`
  - `source_external_id`
  - `source_record_date`
  - `source_record_at`
  - `source_updated_at`
  - `collected_at`
  - `payload_hash`
  - 原始记录主键和原始创建更新时间
- staging 不得为了“看起来干净”而提前删除必要血缘字段。

### intermediate 规范
- intermediate 模型命名必须以 `int_` 开头。
- intermediate 必须以语义整理为目标，不得直接承载最终展示视图。
- intermediate 默认应去除 connector/raw 血缘字段，除非这些字段对语义模型本身仍然必要。
- intermediate 可以做字段类型提升、命名统一、结构展开和跨 staging 组合，但必须保持语义清晰。

### marts 规范
- marts 模型命名必须以 `mart_` 开头。
- marts 必须面向稳定消费场景，如 dashboard、历史查询、指标看板或对外分析口径。
- marts 不得泄漏大量仅供调试的中间字段。
- marts 的粒度必须在模型文档中明确声明。

### 命名规范
- 模型名必须同时体现：
  - 层级前缀
  - 领域
  - 主体或用途
- 列名必须优先使用稳定、语义化的英文 snake_case。
- 不得在列名中混用同义词、缩写和临时命名。

### 文档规范
- 每个模型都必须在对应 `schema.yml` 中声明：
  - `description`
  - 关键列说明
  - 必要的数据类型
  - 关键测试
- 描述必须说明粒度、来源和用途，不得只写“staging table”一类空洞文本。
- 重要派生字段必须说明计算含义或来源规则。

### lineage contract 规范
- 新增、删除、重命名 staging 结构化字段时，必须同步维护 `seeds/staging_lineage_contract.csv`。
- `staging_lineage_contract.csv` 必须是 staging 结构化字段与 raw 路径映射的权威清单。
- 若某 staging 字段来自结构展开、类型转换或多路径组合，也必须在 contract 或相关说明中显式表达。
- 未更新 contract 的 staging 结构变更视为不完整变更。

### 测试规范
- 每个新增模型必须具备适用的唯一性或主键稳定性测试。
- 关键血缘字段必须具备 `not_null` 或等效约束测试。
- staging 变更必须同步补齐：
  - 文档测试
  - 清单测试
  - 类型或血缘相关测试
- intermediate 和 marts 变更必须同步补齐：
  - 粒度测试
  - 结果稳定性测试
  - 关键字段非空或业务约束测试
- 若模型规则变化会影响已有测试语义，必须同时更新测试而不是删掉测试逃避约束。

### materialization 与运行规范
- `staging`、`intermediate`、`marts` 默认必须使用稳定、可查询的关系型 materialization。
- 只有在明确定义收益和边界的情况下，才允许偏离默认策略。
- 模型运行应优先支持分层、路径和标签选择，不得把小范围改动默认升级为整仓全量构建。

### 模型变更流程
- 连接器原始载荷变化时，必须先评估 `raw -> staging -> intermediate -> marts` 的影响面。
- 新增 staging 字段时，必须同步完成：
  - SQL 解析
  - `schema.yml`
  - `staging_lineage_contract.csv`
  - 相关测试
- 新增 marts 时，必须先明确消费对象、粒度和回填逻辑，再编写 SQL。
- 任何跨层字段传播都必须可追踪，不得出现“只在中间层突然出现”的来源不明字段。

## 边界与例外
- 如果 intermediate 模型为了下游 join 或追溯必须保留少量 lineage 字段，可以保留，但必须在模型文档中说明原因。
- 临时分析型 SQL 不得直接进入正式 `models` 目录替代正式模型。
- 仅修改 runner 行为的任务可以不改模型 SQL，但若影响模型发现、执行或产物读取，必须同步维护 runner 测试。

## 实施清单
- 新增模型前先确认所属层级和粒度。
- 写 SQL 时同步规划 `schema.yml` 和测试。
- 修改 staging 字段时同步更新 `staging_lineage_contract.csv`。
- 修改模型选择或运行行为时同步验证 runner 相关逻辑。
- 提交前至少完成适用的 dbt 测试和模型层检查。

## 常见检索词
- `staging`
- `intermediate`
- `marts`
- `lineage contract`
- `schema.yml`
- `模型粒度`
- `字段血缘`
- `命名规范`

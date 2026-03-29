# docs/v1.0 文档入口规范

## 目的
- 为任何新线程提供统一的项目认知入口。
- 为设计、开发、接入、改造和排障类任务提供明确的阅读顺序。
- 将“应该如何做”的规则集中到 `docs/v1.0`，避免开发线程依赖口头约定。

## 适用范围
- 适用于 `frontend`、`admin-api`、`sync-worker`、`dbt`、`connector` 相关任务。
- 适用于功能新增、问题修复、重构、连接器接入、数据模型变更和联调任务。
- 不适用于一次性实验脚本、临时分析产物和未进入 v1.0 范围的预研方案。

## 规范正文

### 阅读顺序
- 新线程在开始任何任务前，必须先阅读：
  1. `README.md`
  2. `technical-architecture.md`
- UI、页面、交互类任务必须继续阅读：
  1. `design-and-prototype-spec.md`
  2. `frontend-development-spec.md`
- 接口、鉴权、任务调度、持久化类任务必须继续阅读：
  1. `backend-development-spec.md`
- 数据仓库、模型、血缘、指标类任务必须继续阅读：
  1. `dbt-development-spec.md`
- 连接器任务必须继续阅读：
  1. `connector/README.md`
  2. 对应连接器专属文档
  3. 若为新增连接器，再阅读 `connector/new-connector-template.md`

### 标准阅读路径
- 页面样式或交互任务：
  `README -> technical-architecture -> design-and-prototype-spec -> frontend-development-spec`
- 前后端联调任务：
  `README -> technical-architecture -> frontend-development-spec -> backend-development-spec`
- 连接器配置或同步任务：
  `README -> technical-architecture -> connector/README -> 对应连接器文档 -> backend-development-spec`
- dbt 模型或看板数据任务：
  `README -> technical-architecture -> dbt-development-spec`
- 新增连接器任务：
  `README -> technical-architecture -> connector/README -> new-connector-template -> 对应专项规范`

### 文档地图
- `technical-architecture.md`
  定义系统模块边界、调用方向、数据流、配置边界和变更落点。
- `design-and-prototype-spec.md`
  定义页面骨架、布局、导航、交互模式、视觉与响应式规则。
- `frontend-development-spec.md`
  定义前端目录职责、分层方式、状态管理、接口调用和样式扩展规范。
- `backend-development-spec.md`
  定义后端接口、异常、分层、持久化、内部调用和测试规范。
- `dbt-development-spec.md`
  定义 dbt 分层、命名、文档、测试、lineage contract 和模型变更规范。
- `connector/README.md`
  定义连接器通用分类、生命周期、配置、安全、原始落库与下游对接规范。
- `connector/garmin-connect.md`
  定义 Garmin Connect 连接器的专属规范。
- `connector/medical-report.md`
  定义 Medical Report 连接器的专属规范。
- `connector/new-connector-template.md`
  定义新增连接器的标准模板与接入清单。

### 术语表
- `frontend`
  用户界面与交互层。
- `admin-api`
  统一业务 API、鉴权入口和跨服务编排层。
- `sync-worker`
  连接器验证、调度和原始同步执行层。
- `dbt-runner`
  dbt 模型清单、运行与产物读取服务层。
- `app schema`
  应用事务数据层，负责账户、用户、连接器配置、任务状态和运行历史。
- `raw schema`
  原始数据落库层，负责保存外部来源的原始载荷与采集血缘。
- `staging`
  原始结构化解析层，负责将 `raw` 数据转成带血缘的结构化表。
- `intermediate`
  语义整理层，负责整理字段语义、清洗和组合，不直接面向最终消费。
- `marts`
  消费层，负责为看板、查询和业务消费提供稳定模型。
- `connector`
  外部数据源接入单元，必须具备配置、验证、同步和下游映射规则。

### 检索规则
- 搜索系统边界，优先检索：`模块边界`、`调用方向`、`数据流`、`变更落点`
- 搜索 UI 规则，优先检索：`布局`、`导航`、`表单`、`弹窗`、`空态`、`响应式`
- 搜索前端规则，优先检索：`service 层`、`mock`、`http`、`store`、`token`
- 搜索后端规则，优先检索：`ApiResult`、`ApiException`、`JWT`、`Flyway`、`内部 API`
- 搜索 dbt 规则，优先检索：`staging`、`intermediate`、`marts`、`lineage contract`
- 搜索连接器规则，优先检索：`接入模式`、`原始落库`、`同步任务`、`配置校验`

## 边界与例外
- 本目录中的文档必须优先表达稳定规范，不承载开发日志、会议记录或临时结论。
- 未被本目录正式定义的页面、模块或连接器视为预留项，不得被新线程默认视为 v1.0 正式范围。
- 若代码实现与规范冲突，处理顺序必须是：
  1. 确认规范是否仍然有效
  2. 若规范有效，则修改代码
  3. 若规范需要升级，则先更新对应文档，再实施代码变更

## 实施清单
- 开始任务前先完成对应阅读路径。
- 做方案判断时必须引用本目录中的规范，而不是口头经验。
- 发现规范空白时，必须补齐文档后再提交大范围实现。
- 提交跨层改动时，必须同时检查是否需要更新多个规范文档。

## 常见检索词
- `接到任务先看什么`
- `技术架构`
- `页面规范`
- `前端开发规范`
- `后端开发规范`
- `dbt 规范`
- `连接器规范`
- `新增连接器模板`

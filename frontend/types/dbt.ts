export type DbtModelLayer = 'staging' | 'intermediate' | 'marts';

export type DbtModelListItem = {
  name: string;
  layer: DbtModelLayer;
  description: string | null;
  connector: string | null;
  domain: string | null;
  connectorKey: string | null;
  domainKey: string | null;
  lastRunCompletedAt: string | null;
};

export type DbtModelListResponse = {
  items: DbtModelListItem[];
};

export type DbtModelColumn = {
  name: string;
  type: string | null;
  description: string | null;
};

export type DbtModelDetail = {
  name: string;
  layer: DbtModelLayer;
  description: string | null;
  connector: string | null;
  domain: string | null;
  lastRunCompletedAt: string | null;
  estimatedRowCount: number | null;
  columns: DbtModelColumn[];
};

export type ListDbtModelsPayload = {
  layer: DbtModelLayer;
  search?: string;
};

export type RunDbtModelPayload = {
  layer: DbtModelLayer;
  modelName: string;
};

export type RunDbtModelResult = {
  success: boolean;
  returncode: number;
  stdout: string;
  stderr: string;
  startedAt?: string | null;
  finishedAt?: string | null;
};

export type DbtBatchRunScopeType = 'connector' | 'domain';

export type RunDbtModelsByScopePayload = {
  layer: DbtModelLayer;
  scopeType: DbtBatchRunScopeType;
  scopeValues: string[];
};

export type RunDbtModelsByScopeItem = {
  modelName: string;
  layer: DbtModelLayer;
  scopeKey: string | null;
  scopeLabel: string | null;
  success: boolean;
  returncode: number | null;
  stdout: string;
  stderr: string;
  startedAt: string | null;
  finishedAt: string | null;
  code: string | null;
  message: string | null;
};

export type RunDbtModelsByScopeResult = {
  layer: DbtModelLayer;
  scopeType: DbtBatchRunScopeType;
  scopeValues: string[];
  totalModels: number;
  succeededCount: number;
  failedCount: number;
  items: RunDbtModelsByScopeItem[];
};

export type DbtRunHistoryListItem = {
  runId: string;
  modelName: string;
  layer: DbtModelLayer;
  status: string;
  returncode: number | null;
  executionTimeSeconds: number | null;
  startedAt: string | null;
  finishedAt: string | null;
};

export type DbtRunHistoryPage = {
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
};

export type DbtRunHistoryListResponse = {
  items: DbtRunHistoryListItem[];
  page: DbtRunHistoryPage;
};

export type ListDbtRunHistoryPayload = {
  page?: number;
  pageSize?: number;
  search?: string;
};

export type DbtRunHistoryDetail = {
  runId: string;
  modelName: string;
  layer: DbtModelLayer;
  status: string;
  returncode: number | null;
  startedAt: string | null;
  finishedAt: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  stdout: string;
  stderr: string;
};

export type DbtRunModelHistoryItem = {
  modelName: string;
  layer: DbtModelLayer;
  status: string;
  completedAt: string | null;
  executionTimeSeconds: number | null;
};

export type DbtRunModelHistoryResponse = {
  items: DbtRunModelHistoryItem[];
};

export type DbtRunLogStream = 'stdout' | 'stderr';

export type DbtRunLogEvent = {
  type: 'log';
  stream: DbtRunLogStream;
  text: string;
  timestamp: string | null;
  targetModelName?: string | null;
};

export type DbtSingleRunStartedEvent = {
  type: 'run_started';
  layer: DbtModelLayer;
  modelName: string;
};

export type DbtSingleRunFinishedEvent = {
  type: 'run_finished';
  layer: DbtModelLayer;
  modelName: string;
  success: boolean;
  returncode: number | null;
  stdout: string;
  stderr: string;
  startedAt: string | null;
  finishedAt: string | null;
  code: string | null;
  message: string | null;
};

export type DbtSingleRunStreamEvent =
  | DbtSingleRunStartedEvent
  | DbtRunLogEvent
  | DbtSingleRunFinishedEvent;

export type DbtBatchRunQueuedItem = {
  modelName: string;
  layer: DbtModelLayer;
  scopeKey: string | null;
  scopeLabel: string | null;
};

export type DbtBatchRunStartedEvent = {
  type: 'batch_started';
  layer: DbtModelLayer;
  scopeType: DbtBatchRunScopeType;
  scopeValues: string[];
  totalModels: number;
  items: DbtBatchRunQueuedItem[];
};

export type DbtBatchTargetStartedEvent = {
  type: 'target_started';
  layer: DbtModelLayer;
  modelName: string;
  scopeKey: string | null;
  scopeLabel: string | null;
};

export type DbtBatchTargetFinishedEvent = {
  type: 'target_finished';
  item: RunDbtModelsByScopeItem;
};

export type DbtBatchRunFinishedEvent = {
  type: 'batch_finished';
  layer: DbtModelLayer;
  scopeType: DbtBatchRunScopeType;
  scopeValues: string[];
  totalModels: number;
  succeededCount: number;
  failedCount: number;
  items: RunDbtModelsByScopeItem[];
};

export type DbtBatchRunStreamEvent =
  | DbtBatchRunStartedEvent
  | DbtBatchTargetStartedEvent
  | DbtRunLogEvent
  | DbtBatchTargetFinishedEvent
  | DbtBatchRunFinishedEvent;

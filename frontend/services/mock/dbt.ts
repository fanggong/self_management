import type { ApiResult } from '~/types/auth';
import type {
  DbtBatchRunStreamEvent,
  DbtBatchRunScopeType,
  DbtModelDetail,
  DbtModelLayer,
  DbtModelListItem,
  DbtModelListResponse,
  DbtSingleRunStreamEvent,
  DbtRunHistoryDetail,
  DbtRunHistoryListItem,
  DbtRunHistoryListResponse,
  DbtRunModelHistoryItem,
  DbtRunModelHistoryResponse,
  ListDbtModelsPayload,
  ListDbtRunHistoryPayload,
  RunDbtModelsByScopePayload,
  RunDbtModelsByScopeResult,
  RunDbtModelPayload,
  RunDbtModelResult
} from '~/types/dbt';

const wait = (ms = 220) => new Promise((resolve) => setTimeout(resolve, ms));
const modelKey = (layer: DbtModelLayer, modelName: string) => `${layer}:${modelName}`;

const createInitialModels = (): Record<DbtModelLayer, DbtModelListItem[]> => ({
  staging: [
    {
      name: 'stg_garmin_profile_snapshot',
      layer: 'staging',
      description: 'Garmin profile snapshot staging table with raw lineage columns.',
      connector: 'Garmin Connect',
      domain: null,
      connectorKey: 'garmin-connect',
      domainKey: null,
      lastRunCompletedAt: '2026-03-18 22:42:56'
    },
    {
      name: 'stg_garmin_daily_summary',
      layer: 'staging',
      description: 'Garmin daily summary staging table for daily wellness metrics.',
      connector: 'Garmin Connect',
      domain: null,
      connectorKey: 'garmin-connect',
      domainKey: null,
      lastRunCompletedAt: '2026-03-18 22:42:56'
    },
    {
      name: 'stg_medical_report_snapshot',
      layer: 'staging',
      description: 'Medical report snapshot staging table with parsed report headers.',
      connector: 'Medical Report',
      domain: null,
      connectorKey: 'medical-report',
      domainKey: null,
      lastRunCompletedAt: '2026-03-18 22:42:56'
    }
  ],
  intermediate: [
    {
      name: 'int_health_profile_snapshot',
      layer: 'intermediate',
      description: 'Semantic intermediate mirror of Garmin profile snapshots.',
      connector: null,
      domain: 'Health',
      connectorKey: null,
      domainKey: 'health',
      lastRunCompletedAt: '2026-03-18 22:42:56'
    },
    {
      name: 'int_health_daily_summary',
      layer: 'intermediate',
      description: 'Semantic intermediate daily summary model for health metrics.',
      connector: null,
      domain: 'Health',
      connectorKey: null,
      domainKey: 'health',
      lastRunCompletedAt: '2026-03-18 22:42:56'
    },
    {
      name: 'int_health_medical_report_snapshot',
      layer: 'intermediate',
      description: 'Semantic intermediate snapshot of parsed medical reports.',
      connector: null,
      domain: 'Health',
      connectorKey: null,
      domainKey: 'health',
      lastRunCompletedAt: '2026-03-18 22:42:56'
    }
  ],
  marts: []
});

const mockModelsState = createInitialModels();
const createInitialModelDetails = (): Record<string, DbtModelDetail> => ({
  [modelKey('staging', 'stg_garmin_profile_snapshot')]: {
    name: 'stg_garmin_profile_snapshot',
    layer: 'staging',
    description: 'Garmin profile snapshot staging table with raw lineage columns.',
    connector: 'Garmin Connect',
    domain: null,
    lastRunCompletedAt: '2026-03-18 22:42:56',
    estimatedRowCount: 2,
    columns: [
      { name: 'raw_record_id', type: 'uuid', description: 'Primary key of the raw parent record.' },
      { name: 'account_id', type: 'uuid', description: 'Application account that owns the staged record.' },
      { name: 'profile_id', type: 'character varying(255)', description: 'Garmin profile identifier.' }
    ]
  },
  [modelKey('staging', 'stg_garmin_daily_summary')]: {
    name: 'stg_garmin_daily_summary',
    layer: 'staging',
    description: 'Garmin daily summary staging table for daily wellness metrics.',
    connector: 'Garmin Connect',
    domain: null,
    lastRunCompletedAt: '2026-03-18 22:42:56',
    estimatedRowCount: 679,
    columns: [
      { name: 'raw_record_id', type: 'uuid', description: 'Primary key of the raw parent record.' },
      { name: 'calendar_date', type: 'date', description: 'Business date of the summary.' },
      { name: 'steps', type: 'integer', description: 'Step count copied from Garmin daily summary.' }
    ]
  },
  [modelKey('staging', 'stg_medical_report_snapshot')]: {
    name: 'stg_medical_report_snapshot',
    layer: 'staging',
    description: 'Medical report snapshot staging table with parsed report headers.',
    connector: 'Medical Report',
    domain: null,
    lastRunCompletedAt: '2026-03-18 22:42:56',
    estimatedRowCount: 1,
    columns: [
      { name: 'raw_record_id', type: 'uuid', description: 'Primary key of the raw parent record.' },
      { name: 'record_number', type: 'character varying(255)', description: 'Medical report number.' },
      { name: 'institution', type: 'character varying(255)', description: 'Medical institution name.' }
    ]
  },
  [modelKey('intermediate', 'int_health_profile_snapshot')]: {
    name: 'int_health_profile_snapshot',
    layer: 'intermediate',
    description: 'Semantic intermediate mirror of Garmin profile snapshots.',
    connector: null,
    domain: 'Health',
    lastRunCompletedAt: '2026-03-18 22:42:56',
    estimatedRowCount: 2,
    columns: [
      { name: 'staging_record_id', type: 'uuid', description: 'Technical lineage key from staging.' },
      { name: 'account_id', type: 'uuid', description: 'Application account that owns the health record.' },
      { name: 'profile_id', type: 'character varying(255)', description: 'Semantic Garmin profile identifier.' }
    ]
  },
  [modelKey('intermediate', 'int_health_daily_summary')]: {
    name: 'int_health_daily_summary',
    layer: 'intermediate',
    description: 'Semantic intermediate daily summary model for health metrics.',
    connector: null,
    domain: 'Health',
    lastRunCompletedAt: '2026-03-18 22:42:56',
    estimatedRowCount: 679,
    columns: [
      { name: 'staging_record_id', type: 'uuid', description: 'Technical lineage key from staging.' },
      { name: 'summary_date', type: 'date', description: 'Summary business date.' },
      { name: 'step_count', type: 'integer', description: 'Semantic step count metric.' }
    ]
  },
  [modelKey('intermediate', 'int_health_medical_report_snapshot')]: {
    name: 'int_health_medical_report_snapshot',
    layer: 'intermediate',
    description: 'Semantic intermediate snapshot of parsed medical reports.',
    connector: null,
    domain: 'Health',
    lastRunCompletedAt: '2026-03-18 22:42:56',
    estimatedRowCount: 1,
    columns: [
      { name: 'staging_record_id', type: 'uuid', description: 'Technical lineage key from staging.' },
      { name: 'record_number', type: 'character varying(255)', description: 'Medical report number.' },
      { name: 'report_date', type: 'date', description: 'Medical report date.' }
    ]
  }
});
const mockModelDetailState = createInitialModelDetails();
let mockRunSequence = 12;

const createInitialRunHistory = (): DbtRunHistoryDetail[] => [
  {
    runId: 'run-12',
    modelName: 'int_health_profile_snapshot',
    layer: 'intermediate',
    status: 'FAILED',
    returncode: 1,
    startedAt: '2026-03-22 20:18:12',
    finishedAt: '2026-03-22 20:18:14',
    errorCode: 'DBT_MODEL_RUN_FAILED',
    errorMessage: 'Database Error in model int_health_profile_snapshot',
    stdout: 'Running dbt model int_health_profile_snapshot\n1 of 2 OK created sql table model stg_garmin_profile_snapshot',
    stderr: 'column stg.connection_request_id does not exist'
  },
  {
    runId: 'run-11',
    modelName: 'stg_garmin_daily_summary',
    layer: 'staging',
    status: 'SUCCESS',
    returncode: 0,
    startedAt: '2026-03-22 18:42:02',
    finishedAt: '2026-03-22 18:42:04',
    errorCode: null,
    errorMessage: null,
    stdout: 'Running dbt model stg_garmin_daily_summary\nDone.',
    stderr: ''
  },
  {
    runId: 'run-10',
    modelName: 'int_health_daily_summary',
    layer: 'intermediate',
    status: 'SUCCESS',
    returncode: 0,
    startedAt: '2026-03-22 17:30:20',
    finishedAt: '2026-03-22 17:30:24',
    errorCode: null,
    errorMessage: null,
    stdout: 'Running dbt model int_health_daily_summary\nDone.',
    stderr: ''
  },
  {
    runId: 'run-9',
    modelName: 'stg_medical_report_snapshot',
    layer: 'staging',
    status: 'SUCCESS',
    returncode: 0,
    startedAt: '2026-03-22 16:15:11',
    finishedAt: '2026-03-22 16:15:13',
    errorCode: null,
    errorMessage: null,
    stdout: 'Running dbt model stg_medical_report_snapshot\nDone.',
    stderr: ''
  },
  {
    runId: 'run-8',
    modelName: 'int_health_medical_report_snapshot',
    layer: 'intermediate',
    status: 'SUCCESS',
    returncode: 0,
    startedAt: '2026-03-22 15:03:45',
    finishedAt: '2026-03-22 15:03:50',
    errorCode: null,
    errorMessage: null,
    stdout: 'Running dbt model int_health_medical_report_snapshot\nDone.',
    stderr: ''
  },
  {
    runId: 'run-7',
    modelName: 'stg_garmin_profile_snapshot',
    layer: 'staging',
    status: 'SUCCESS',
    returncode: 0,
    startedAt: '2026-03-22 13:52:06',
    finishedAt: '2026-03-22 13:52:08',
    errorCode: null,
    errorMessage: null,
    stdout: 'Running dbt model stg_garmin_profile_snapshot\nDone.',
    stderr: ''
  },
  {
    runId: 'run-6',
    modelName: 'int_health_profile_snapshot',
    layer: 'intermediate',
    status: 'SUCCESS',
    returncode: 0,
    startedAt: '2026-03-22 12:42:36',
    finishedAt: '2026-03-22 12:42:40',
    errorCode: null,
    errorMessage: null,
    stdout: 'Running dbt model int_health_profile_snapshot\nDone.',
    stderr: ''
  },
  {
    runId: 'run-5',
    modelName: 'mart_health_daily_rollup',
    layer: 'marts',
    status: 'SUCCESS',
    returncode: 0,
    startedAt: '2026-03-22 11:10:10',
    finishedAt: '2026-03-22 11:10:17',
    errorCode: null,
    errorMessage: null,
    stdout: 'Running dbt model mart_health_daily_rollup\nDone.',
    stderr: ''
  },
  {
    runId: 'run-4',
    modelName: 'mart_health_profile_latest',
    layer: 'marts',
    status: 'RUNNER_ERROR',
    returncode: null,
    startedAt: null,
    finishedAt: null,
    errorCode: 'DBT_RUNNER_UNAVAILABLE',
    errorMessage: 'dbt runner is temporarily unavailable.',
    stdout: '',
    stderr: ''
  },
  {
    runId: 'run-3',
    modelName: 'stg_garmin_daily_summary',
    layer: 'staging',
    status: 'SUCCESS',
    returncode: 0,
    startedAt: '2026-03-21 21:08:02',
    finishedAt: '2026-03-21 21:08:04',
    errorCode: null,
    errorMessage: null,
    stdout: 'Running dbt model stg_garmin_daily_summary\nDone.',
    stderr: ''
  },
  {
    runId: 'run-2',
    modelName: 'int_health_daily_summary',
    layer: 'intermediate',
    status: 'BUSY',
    returncode: null,
    startedAt: null,
    finishedAt: null,
    errorCode: 'DBT_RUNNER_BUSY',
    errorMessage: 'Another dbt command is already running.',
    stdout: '',
    stderr: ''
  },
  {
    runId: 'run-1',
    modelName: 'mart_health_adherence_weekly',
    layer: 'marts',
    status: 'SUCCESS',
    returncode: 0,
    startedAt: '2026-03-20 09:08:02',
    finishedAt: '2026-03-20 09:08:12',
    errorCode: null,
    errorMessage: null,
    stdout: 'Running dbt model mart_health_adherence_weekly\nDone.',
    stderr: ''
  }
];

const mockRunHistoryState = createInitialRunHistory();
const createInitialRunModelHistory = (): Record<string, DbtRunModelHistoryItem[]> => ({
  'run-12': [
    {
      modelName: 'stg_garmin_profile_snapshot',
      layer: 'staging',
      status: 'success',
      completedAt: '2026-03-22 20:18:13',
      executionTimeSeconds: 0.06
    },
    {
      modelName: 'int_health_profile_snapshot',
      layer: 'intermediate',
      status: 'error',
      completedAt: '2026-03-22 20:18:14',
      executionTimeSeconds: 0.01
    }
  ],
  'run-11': [
    {
      modelName: 'stg_garmin_daily_summary',
      layer: 'staging',
      status: 'success',
      completedAt: '2026-03-22 18:42:04',
      executionTimeSeconds: 0.04
    }
  ],
  'run-10': [
    {
      modelName: 'stg_garmin_daily_summary',
      layer: 'staging',
      status: 'success',
      completedAt: '2026-03-22 17:30:22',
      executionTimeSeconds: 0.05
    },
    {
      modelName: 'int_health_daily_summary',
      layer: 'intermediate',
      status: 'success',
      completedAt: '2026-03-22 17:30:24',
      executionTimeSeconds: 0.07
    }
  ],
  'run-9': [
    {
      modelName: 'stg_medical_report_snapshot',
      layer: 'staging',
      status: 'success',
      completedAt: '2026-03-22 16:15:13',
      executionTimeSeconds: 0.03
    }
  ],
  'run-8': [
    {
      modelName: 'stg_medical_report_snapshot',
      layer: 'staging',
      status: 'success',
      completedAt: '2026-03-22 15:03:47',
      executionTimeSeconds: 0.03
    },
    {
      modelName: 'int_health_medical_report_snapshot',
      layer: 'intermediate',
      status: 'success',
      completedAt: '2026-03-22 15:03:50',
      executionTimeSeconds: 0.08
    }
  ],
  'run-7': [
    {
      modelName: 'stg_garmin_profile_snapshot',
      layer: 'staging',
      status: 'success',
      completedAt: '2026-03-22 13:52:08',
      executionTimeSeconds: 0.05
    }
  ],
  'run-6': [
    {
      modelName: 'stg_garmin_profile_snapshot',
      layer: 'staging',
      status: 'success',
      completedAt: '2026-03-22 12:42:38',
      executionTimeSeconds: 0.04
    },
    {
      modelName: 'int_health_profile_snapshot',
      layer: 'intermediate',
      status: 'success',
      completedAt: '2026-03-22 12:42:40',
      executionTimeSeconds: 0.06
    }
  ],
  'run-5': [
    {
      modelName: 'mart_health_daily_rollup',
      layer: 'marts',
      status: 'success',
      completedAt: '2026-03-22 11:10:17',
      executionTimeSeconds: 0.12
    }
  ],
  'run-4': [],
  'run-3': [
    {
      modelName: 'stg_garmin_daily_summary',
      layer: 'staging',
      status: 'success',
      completedAt: '2026-03-21 21:08:04',
      executionTimeSeconds: 0.04
    }
  ],
  'run-2': [],
  'run-1': [
    {
      modelName: 'mart_health_adherence_weekly',
      layer: 'marts',
      status: 'success',
      completedAt: '2026-03-20 09:08:12',
      executionTimeSeconds: 0.17
    }
  ]
});
const mockRunModelHistoryState = createInitialRunModelHistory();

const cloneItem = (item: DbtModelListItem): DbtModelListItem => ({
  ...item
});

const cloneLayerItems = (layer: DbtModelLayer): DbtModelListItem[] => mockModelsState[layer].map((item) => cloneItem(item));
const cloneModelDetail = (layer: DbtModelLayer, modelName: string): DbtModelDetail | null => {
  const item = mockModelDetailState[modelKey(layer, modelName)];
  return item ? { ...item, columns: item.columns.map((column) => ({ ...column })) } : null;
};
const cloneRunHistoryDetail = (item: DbtRunHistoryDetail): DbtRunHistoryDetail => ({ ...item });
const cloneRunModelHistoryItems = (runId: string): DbtRunModelHistoryItem[] => (mockRunModelHistoryState[runId] ?? []).map((item) => ({ ...item }));

const formatDateTime = (date: Date) => {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
};

const nextRunId = () => {
  mockRunSequence += 1;
  return `run-${mockRunSequence}`;
};

const toRunHistoryListItem = (item: DbtRunHistoryDetail): DbtRunHistoryListItem => ({
  runId: item.runId,
  modelName: item.modelName,
  layer: item.layer,
  status: item.status,
  returncode: item.returncode,
  executionTimeSeconds: item.startedAt && item.finishedAt
    ? (new Date(item.finishedAt.replace(' ', 'T')).getTime() - new Date(item.startedAt.replace(' ', 'T')).getTime()) / 1000
    : null,
  startedAt: item.startedAt,
  finishedAt: item.finishedAt
});

const matchesRunHistorySearch = (item: DbtRunHistoryDetail, search: string) => {
  if (!search) {
    return true;
  }

  return item.modelName.toLowerCase().includes(search)
    || item.layer.toLowerCase().includes(search)
    || item.status.toLowerCase().includes(search);
};

const compareRunHistory = (left: DbtRunHistoryDetail, right: DbtRunHistoryDetail) => {
  if (left.startedAt && right.startedAt) {
    return right.startedAt.localeCompare(left.startedAt);
  }
  if (left.startedAt) {
    return -1;
  }
  if (right.startedAt) {
    return 1;
  }
  return right.runId.localeCompare(left.runId);
};

const createRunHistoryEntry = (
  model: DbtModelListItem,
  startedAt: Date,
  finishedAt: Date
): DbtRunHistoryDetail => ({
  runId: nextRunId(),
  modelName: model.name,
  layer: model.layer,
  status: 'SUCCESS',
  returncode: 0,
  startedAt: formatDateTime(startedAt),
  finishedAt: formatDateTime(finishedAt),
  errorCode: null,
  errorMessage: null,
  stdout: `Running dbt model +${model.name}\n1 of 1 OK created sql table model ${model.name}\nDone.`,
  stderr: ''
});

const performMockModelRun = (model: DbtModelListItem) => {
  const startedAt = new Date();
  const finishedAt = new Date(startedAt.getTime() + 1400);
  model.lastRunCompletedAt = formatDateTime(finishedAt);
  const detail = mockModelDetailState[modelKey(model.layer, model.name)];
  if (detail) {
    detail.lastRunCompletedAt = model.lastRunCompletedAt;
  }

  const historyEntry = createRunHistoryEntry(model, startedAt, finishedAt);
  mockRunHistoryState.unshift(historyEntry);
  mockRunModelHistoryState[historyEntry.runId] = [
    {
      modelName: model.name,
      layer: model.layer,
      status: 'success',
      completedAt: formatDateTime(finishedAt),
      executionTimeSeconds: 0.05
    }
  ];

  return {
    historyEntry,
    result: {
      success: true,
      returncode: 0,
      stdout: `Running dbt model ${model.name}\n1 of 1 OK created sql table model ${model.name}\nDone.`,
      stderr: '',
      startedAt: formatDateTime(startedAt),
      finishedAt: formatDateTime(finishedAt)
    } satisfies RunDbtModelResult
  };
};

const resolveScopeKey = (item: DbtModelListItem, scopeType: DbtBatchRunScopeType) =>
  scopeType === 'connector' ? item.connectorKey : item.domainKey;

const resolveScopeLabel = (item: DbtModelListItem, scopeType: DbtBatchRunScopeType) =>
  scopeType === 'connector' ? item.connector : item.domain;

const emitSingleRunLogs = async (
  model: DbtModelListItem,
  onEvent?: (event: DbtSingleRunStreamEvent) => void
) => {
  onEvent?.({
    type: 'run_started',
    layer: model.layer,
    modelName: model.name
  });

  const lines = [
    `Running with dbt=1.11.7\n`,
    `Registered adapter: postgres=1.9.0\n`,
    `Starting model ${model.name}\n`,
    `Finished model ${model.name}\n`
  ];
  for (const line of lines) {
    await wait(140);
    onEvent?.({
      type: 'log',
      stream: 'stdout',
      text: line,
      timestamp: formatDateTime(new Date())
    });
  }
};

const emitBatchModelLogs = async (
  model: DbtModelListItem,
  onEvent?: (event: DbtBatchRunStreamEvent) => void
) => {
  const lines = [
    `Running with dbt=1.11.7\n`,
    `Queued upstream dependencies for ${model.name}\n`,
    `Executing target model ${model.name}\n`,
    `Finished target model ${model.name}\n`
  ];

  for (const line of lines) {
    await wait(120);
    onEvent?.({
      type: 'log',
      stream: 'stdout',
      text: line,
      timestamp: formatDateTime(new Date()),
      targetModelName: model.name
    });
  }
};

export const mockDbtModelApi = {
  async list(payload: ListDbtModelsPayload): Promise<ApiResult<DbtModelListResponse>> {
    await wait(120);

    const searchValue = payload.search?.trim().toLowerCase() ?? '';
    const items = cloneLayerItems(payload.layer)
      .filter((item) => !searchValue || item.name.toLowerCase().includes(searchValue))
      .sort((left, right) => left.name.localeCompare(right.name));

    return {
      success: true,
      data: { items }
    };
  },

  async getDetail(layer: DbtModelLayer, modelName: string): Promise<ApiResult<DbtModelDetail>> {
    await wait(120);

    const item = cloneModelDetail(layer, modelName);
    if (!item) {
      return {
        success: false,
        message: 'Model not found.',
        code: 'MODEL_NOT_FOUND'
      };
    }

    return {
      success: true,
      data: item
    };
  },

  async run(payload: RunDbtModelPayload): Promise<ApiResult<RunDbtModelResult>> {
    await wait(500);

    const items = mockModelsState[payload.layer];
    const model = items.find((item) => item.name === payload.modelName);
    if (!model) {
      return {
        success: false,
        message: 'Model not found.',
        code: 'MODEL_NOT_FOUND'
      };
    }

    const { result } = performMockModelRun(model);

    return {
      success: true,
      data: result,
      message: `${payload.modelName} run completed.`
    };
  },

  async streamRun(
    payload: RunDbtModelPayload,
    onEvent?: (event: DbtSingleRunStreamEvent) => void
  ): Promise<ApiResult<RunDbtModelResult>> {
    await wait(120);

    const model = mockModelsState[payload.layer].find((item) => item.name === payload.modelName);
    if (!model) {
      return {
        success: false,
        message: 'Model not found.',
        code: 'MODEL_NOT_FOUND'
      };
    }

    await emitSingleRunLogs(model, onEvent);
    const { result } = performMockModelRun(model);
    onEvent?.({
      type: 'run_finished',
      layer: model.layer,
      modelName: model.name,
      success: true,
      returncode: result.returncode,
      stdout: result.stdout,
      stderr: result.stderr,
      startedAt: result.startedAt ?? null,
      finishedAt: result.finishedAt ?? null,
      code: null,
      message: `${model.name} run completed.`
    });

    return {
      success: true,
      data: result,
      message: `${payload.modelName} run completed.`
    };
  },

  async runBatch(payload: RunDbtModelsByScopePayload): Promise<ApiResult<RunDbtModelsByScopeResult>> {
    await wait(700);

    const scopeValues = payload.scopeValues.map((value) => value.trim().toLowerCase()).filter(Boolean);
    const matchedModels = cloneLayerItems(payload.layer)
      .filter((item) => {
        const scopeKey = resolveScopeKey(item, payload.scopeType);
        return scopeKey ? scopeValues.includes(scopeKey) : false;
      })
      .sort((left, right) => left.name.localeCompare(right.name));

    const items = matchedModels.map((matchedModel) => {
      const stateModel = mockModelsState[payload.layer].find((item) => item.name === matchedModel.name);
      if (!stateModel) {
        return {
          modelName: matchedModel.name,
          layer: matchedModel.layer,
          scopeKey: resolveScopeKey(matchedModel, payload.scopeType),
          scopeLabel: resolveScopeLabel(matchedModel, payload.scopeType),
          success: false,
          returncode: null,
          stdout: '',
          stderr: '',
          startedAt: null,
          finishedAt: null,
          code: 'MODEL_NOT_FOUND',
          message: 'Model not found.'
        };
      }

      const { result } = performMockModelRun(stateModel);
      return {
        modelName: stateModel.name,
        layer: stateModel.layer,
        scopeKey: resolveScopeKey(stateModel, payload.scopeType),
        scopeLabel: resolveScopeLabel(stateModel, payload.scopeType),
        success: true,
        returncode: result.returncode,
        stdout: result.stdout,
        stderr: result.stderr,
        startedAt: result.startedAt ?? null,
        finishedAt: result.finishedAt ?? null,
        code: null,
        message: `${stateModel.name} run completed.`
      };
    });

    const succeededCount = items.filter((item) => item.success).length;

    return {
      success: true,
      data: {
        layer: payload.layer,
        scopeType: payload.scopeType,
        scopeValues,
        totalModels: items.length,
        succeededCount,
        failedCount: items.length - succeededCount,
        items
      }
    };
  },

  async streamRunBatch(
    payload: RunDbtModelsByScopePayload,
    onEvent?: (event: DbtBatchRunStreamEvent) => void
  ): Promise<ApiResult<RunDbtModelsByScopeResult>> {
    await wait(120);

    const scopeValues = payload.scopeValues.map((value) => value.trim().toLowerCase()).filter(Boolean);
    const matchedModels = cloneLayerItems(payload.layer)
      .filter((item) => {
        const scopeKey = resolveScopeKey(item, payload.scopeType);
        return scopeKey ? scopeValues.includes(scopeKey) : false;
      })
      .sort((left, right) => left.name.localeCompare(right.name));

    onEvent?.({
      type: 'batch_started',
      layer: payload.layer,
      scopeType: payload.scopeType,
      scopeValues,
      totalModels: matchedModels.length,
      items: matchedModels.map((item) => ({
        modelName: item.name,
        layer: item.layer,
        scopeKey: resolveScopeKey(item, payload.scopeType),
        scopeLabel: resolveScopeLabel(item, payload.scopeType)
      }))
    });

    const items: RunDbtModelsByScopeResult['items'] = [];
    for (const matchedModel of matchedModels) {
      const stateModel = mockModelsState[payload.layer].find((item) => item.name === matchedModel.name);
      if (!stateModel) {
        const missingItem = {
          modelName: matchedModel.name,
          layer: matchedModel.layer,
          scopeKey: resolveScopeKey(matchedModel, payload.scopeType),
          scopeLabel: resolveScopeLabel(matchedModel, payload.scopeType),
          success: false,
          returncode: null,
          stdout: '',
          stderr: '',
          startedAt: null,
          finishedAt: null,
          code: 'MODEL_NOT_FOUND',
          message: 'Model not found.'
        };
        onEvent?.({
          type: 'target_started',
          layer: matchedModel.layer,
          modelName: matchedModel.name,
          scopeKey: missingItem.scopeKey,
          scopeLabel: missingItem.scopeLabel
        });
        onEvent?.({ type: 'target_finished', item: missingItem });
        items.push(missingItem);
        continue;
      }

      onEvent?.({
        type: 'target_started',
        layer: stateModel.layer,
        modelName: stateModel.name,
        scopeKey: resolveScopeKey(stateModel, payload.scopeType),
        scopeLabel: resolveScopeLabel(stateModel, payload.scopeType)
      });
      await emitBatchModelLogs(stateModel, onEvent);
      const { result } = performMockModelRun(stateModel);
      const finishedItem = {
        modelName: stateModel.name,
        layer: stateModel.layer,
        scopeKey: resolveScopeKey(stateModel, payload.scopeType),
        scopeLabel: resolveScopeLabel(stateModel, payload.scopeType),
        success: true,
        returncode: result.returncode,
        stdout: result.stdout,
        stderr: result.stderr,
        startedAt: result.startedAt ?? null,
        finishedAt: result.finishedAt ?? null,
        code: null,
        message: `${stateModel.name} run completed.`
      };
      onEvent?.({ type: 'target_finished', item: finishedItem });
      items.push(finishedItem);
    }

    const succeededCount = items.filter((item) => item.success).length;
    onEvent?.({
      type: 'batch_finished',
      layer: payload.layer,
      scopeType: payload.scopeType,
      scopeValues,
      totalModels: items.length,
      succeededCount,
      failedCount: items.length - succeededCount,
      items
    });

    return {
      success: true,
      data: {
        layer: payload.layer,
        scopeType: payload.scopeType,
        scopeValues,
        totalModels: items.length,
        succeededCount,
        failedCount: items.length - succeededCount,
        items
      }
    };
  },

  async listRuns(payload: ListDbtRunHistoryPayload): Promise<ApiResult<DbtRunHistoryListResponse>> {
    await wait(140);

    const page = payload.page ?? 1;
    const pageSize = payload.pageSize ?? 10;
    const searchValue = payload.search?.trim().toLowerCase() ?? '';
    const filtered = mockRunHistoryState
      .filter((item) => matchesRunHistorySearch(item, searchValue))
      .sort(compareRunHistory);

    const total = filtered.length;
    const totalPages = total === 0 ? 0 : Math.ceil(total / pageSize);
    const from = Math.max(0, (page - 1) * pageSize);
    const to = from + pageSize;

    return {
      success: true,
      data: {
        items: filtered.slice(from, to).map((item) => toRunHistoryListItem(item)),
        page: {
          page,
          pageSize,
          total,
          totalPages
        }
      }
    };
  },

  async getRunDetail(runId: string): Promise<ApiResult<DbtRunHistoryDetail>> {
    await wait(120);

    const item = mockRunHistoryState.find((entry) => entry.runId === runId);
    if (!item) {
      return {
        success: false,
        message: 'Run history was not found.',
        code: 'DBT_RUN_HISTORY_NOT_FOUND'
      };
    }

    return {
      success: true,
      data: cloneRunHistoryDetail(item)
    };
  },

  async listRunModels(runId: string): Promise<ApiResult<DbtRunModelHistoryResponse>> {
    await wait(100);

    const parentRun = mockRunHistoryState.find((entry) => entry.runId === runId);
    if (!parentRun) {
      return {
        success: false,
        message: 'Run history was not found.',
        code: 'DBT_RUN_HISTORY_NOT_FOUND'
      };
    }

    return {
      success: true,
      data: {
        items: cloneRunModelHistoryItems(runId)
      }
    };
  }
};

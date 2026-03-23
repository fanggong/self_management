import type { ApiResult } from '~/types/auth';
import type {
  DbtModelLayer,
  DbtModelListItem,
  DbtModelListResponse,
  DbtRunHistoryDetail,
  DbtRunHistoryListItem,
  DbtRunHistoryListResponse,
  ListDbtModelsPayload,
  ListDbtRunHistoryPayload,
  RunDbtModelPayload,
  RunDbtModelResult
} from '~/types/dbt';

const wait = (ms = 220) => new Promise((resolve) => setTimeout(resolve, ms));

const createInitialModels = (): Record<DbtModelLayer, DbtModelListItem[]> => ({
  staging: [
    { name: 'stg_garmin_profile_snapshot', layer: 'staging', lastRunCompletedAt: '2026-03-18 22:42:56' },
    { name: 'stg_garmin_daily_summary', layer: 'staging', lastRunCompletedAt: '2026-03-18 22:42:56' },
    { name: 'stg_medical_report_snapshot', layer: 'staging', lastRunCompletedAt: '2026-03-18 22:42:56' }
  ],
  intermediate: [
    { name: 'int_health_profile_snapshot', layer: 'intermediate', lastRunCompletedAt: '2026-03-18 22:42:56' },
    { name: 'int_health_daily_summary', layer: 'intermediate', lastRunCompletedAt: '2026-03-18 22:42:56' },
    { name: 'int_health_medical_report_snapshot', layer: 'intermediate', lastRunCompletedAt: '2026-03-18 22:42:56' }
  ],
  marts: []
});

const mockModelsState = createInitialModels();
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

const cloneItem = (item: DbtModelListItem): DbtModelListItem => ({
  ...item
});

const cloneLayerItems = (layer: DbtModelLayer): DbtModelListItem[] => mockModelsState[layer].map((item) => cloneItem(item));
const cloneRunHistoryDetail = (item: DbtRunHistoryDetail): DbtRunHistoryDetail => ({ ...item });

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

    const startedAt = new Date();
    const finishedAt = new Date(startedAt.getTime() + 1400);
    model.lastRunCompletedAt = formatDateTime(finishedAt);

    const historyEntry: DbtRunHistoryDetail = {
      runId: nextRunId(),
      modelName: payload.modelName,
      layer: payload.layer,
      status: 'SUCCESS',
      returncode: 0,
      startedAt: formatDateTime(startedAt),
      finishedAt: formatDateTime(finishedAt),
      errorCode: null,
      errorMessage: null,
      stdout: `Running dbt model +${payload.modelName}\n1 of 1 OK created sql table model ${payload.modelName}\nDone.`,
      stderr: ''
    };
    mockRunHistoryState.unshift(historyEntry);

    return {
      success: true,
      data: {
        success: true,
        returncode: 0,
        stdout: `Running dbt model ${payload.modelName}\n1 of 1 OK created sql table model ${payload.modelName}\nDone.`,
        stderr: '',
        startedAt: formatDateTime(startedAt),
        finishedAt: formatDateTime(finishedAt)
      },
      message: `${payload.modelName} run completed.`
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
  }
};

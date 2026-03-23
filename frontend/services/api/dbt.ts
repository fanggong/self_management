import type { ApiResult } from '~/types/auth';
import type {
  DbtBatchRunFinishedEvent,
  DbtBatchRunStreamEvent,
  DbtModelDetail,
  DbtModelLayer,
  DbtModelListResponse,
  DbtSingleRunFinishedEvent,
  DbtSingleRunStreamEvent,
  RunDbtModelsByScopePayload,
  RunDbtModelsByScopeResult,
  DbtRunHistoryDetail,
  DbtRunHistoryListResponse,
  DbtRunModelHistoryResponse,
  ListDbtModelsPayload,
  ListDbtRunHistoryPayload,
  RunDbtModelPayload,
  RunDbtModelResult
} from '~/types/dbt';
import { requestApi, useHttpApiMode } from '~/services/api/http';
import { mockDbtModelApi } from '~/services/mock/dbt';

const buildDbtModelsPath = (payload: ListDbtModelsPayload) => {
  const params = new URLSearchParams();
  params.set('layer', payload.layer);
  if (payload.search?.trim()) {
    params.set('search', payload.search.trim());
  }
  return `/users/me/dbt-models?${params.toString()}`;
};

const buildDbtModelDetailPath = (layer: DbtModelLayer, modelName: string) =>
  `/users/me/dbt-models/${encodeURIComponent(layer)}/${encodeURIComponent(modelName)}`;

const buildDbtRunHistoryPath = (payload: ListDbtRunHistoryPayload) => {
  const params = new URLSearchParams();
  params.set('page', String(payload.page ?? 1));
  params.set('pageSize', String(payload.pageSize ?? 10));
  if (payload.search?.trim()) {
    params.set('search', payload.search.trim());
  }

  return `/users/me/dbt-model-runs?${params.toString()}`;
};

const TOKEN_COOKIE_KEY = 'sm_auth_token';

const isApiResultPayload = <T>(value: unknown): value is ApiResult<T> => {
  if (!value || typeof value !== 'object') {
    return false;
  }

  const maybeResult = value as Partial<ApiResult<T>>;
  return typeof maybeResult.success === 'boolean'
    && ('message' in maybeResult)
    && ('code' in maybeResult);
};

const handleUnauthorized = () => {
  if (!import.meta.client) {
    return;
  }

  const tokenCookie = useCookie<string | null>(TOKEN_COOKIE_KEY, { sameSite: 'lax' });
  tokenCookie.value = null;

  if (window.location.pathname !== '/login') {
    window.location.assign('/login');
  }
};

const resolveStreamApiBase = () => {
  const config = useRuntimeConfig();
  return import.meta.server ? config.apiBaseInternal : config.public.apiBase;
};

const parseApiErrorResponse = async <T>(response: Response): Promise<ApiResult<T>> => {
  try {
    const payload = await response.json();
    if (isApiResultPayload<T>(payload)) {
      if (response.status === 401) {
        handleUnauthorized();
      }
      return payload;
    }
  } catch {
    // Fall through to the generic error payload below.
  }

  if (response.status === 401) {
    handleUnauthorized();
    return {
      success: false,
      message: 'Session expired. Please log in again.',
      code: 'UNAUTHORIZED'
    };
  }

  return {
    success: false,
    message: `Request failed with HTTP ${response.status}.`,
    code: 'REQUEST_ERROR'
  };
};

const readNdjsonEvents = async <TEvent extends { type: string }>(
  response: Response,
  onEvent?: (event: TEvent) => void
) => {
  if (!response.body) {
    throw new Error('Response body is not readable.');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  const events: TEvent[] = [];

  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop() ?? '';

    for (const line of lines) {
      const normalized = line.trim();
      if (!normalized) {
        continue;
      }

      const event = JSON.parse(normalized) as TEvent;
      events.push(event);
      onEvent?.(event);
    }
  }

  const tail = (buffer + decoder.decode()).trim();
  if (tail) {
    const event = JSON.parse(tail) as TEvent;
    events.push(event);
    onEvent?.(event);
  }

  return events;
};

const streamSingleRunHttp = async (
  token: string | null | undefined,
  payload: RunDbtModelPayload,
  onEvent?: (event: DbtSingleRunStreamEvent) => void
): Promise<ApiResult<RunDbtModelResult>> => {
  const response = await fetch(`${resolveStreamApiBase()}/users/me/dbt-models/run-stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/x-ndjson, application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(payload)
  });

  const contentType = response.headers.get('content-type') ?? '';
  if (!response.ok || !contentType.toLowerCase().includes('application/x-ndjson')) {
    return parseApiErrorResponse(response);
  }

  const events = await readNdjsonEvents<DbtSingleRunStreamEvent>(response, onEvent);
  const finalEvent = [...events].reverse().find((event): event is DbtSingleRunFinishedEvent => event.type === 'run_finished');
  if (!finalEvent) {
    return {
      success: false,
      message: 'dbt run stream ended without a final result.',
      code: 'DBT_STREAM_INCOMPLETE'
    };
  }

  return {
    success: finalEvent.success,
    data: {
      success: finalEvent.success,
      returncode: finalEvent.returncode ?? 0,
      stdout: finalEvent.stdout,
      stderr: finalEvent.stderr,
      startedAt: finalEvent.startedAt,
      finishedAt: finalEvent.finishedAt
    },
    message: finalEvent.message ?? (finalEvent.success ? 'dbt model run completed.' : 'dbt model run failed.'),
    code: finalEvent.code ?? (finalEvent.success ? undefined : 'DBT_MODEL_RUN_FAILED')
  };
};

const streamBatchRunHttp = async (
  token: string | null | undefined,
  payload: RunDbtModelsByScopePayload,
  onEvent?: (event: DbtBatchRunStreamEvent) => void
): Promise<ApiResult<RunDbtModelsByScopeResult>> => {
  const response = await fetch(`${resolveStreamApiBase()}/users/me/dbt-models/run-batch-stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/x-ndjson, application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(payload)
  });

  const contentType = response.headers.get('content-type') ?? '';
  if (!response.ok || !contentType.toLowerCase().includes('application/x-ndjson')) {
    return parseApiErrorResponse(response);
  }

  const events = await readNdjsonEvents<DbtBatchRunStreamEvent>(response, onEvent);
  const finalEvent = [...events].reverse().find((event): event is DbtBatchRunFinishedEvent => event.type === 'batch_finished');
  if (!finalEvent) {
    return {
      success: false,
      message: 'dbt batch run stream ended without a final result.',
      code: 'DBT_BATCH_STREAM_INCOMPLETE'
    };
  }

  return {
    success: true,
    data: {
      layer: finalEvent.layer,
      scopeType: finalEvent.scopeType,
      scopeValues: finalEvent.scopeValues,
      totalModels: finalEvent.totalModels,
      succeededCount: finalEvent.succeededCount,
      failedCount: finalEvent.failedCount,
      items: finalEvent.items
    }
  };
};

export const dbtModelApi = {
  list(token: string | null | undefined, payload: ListDbtModelsPayload): Promise<ApiResult<DbtModelListResponse>> {
    if (useHttpApiMode()) {
      return requestApi<DbtModelListResponse>(buildDbtModelsPath(payload), {
        token
      });
    }

    return mockDbtModelApi.list(payload);
  },

  getDetail(token: string | null | undefined, layer: DbtModelLayer, modelName: string): Promise<ApiResult<DbtModelDetail>> {
    if (useHttpApiMode()) {
      return requestApi<DbtModelDetail>(buildDbtModelDetailPath(layer, modelName), {
        token
      });
    }

    return mockDbtModelApi.getDetail(layer, modelName);
  },

  run(token: string | null | undefined, payload: RunDbtModelPayload): Promise<ApiResult<RunDbtModelResult>> {
    if (useHttpApiMode()) {
      return requestApi<RunDbtModelResult>('/users/me/dbt-models/run', {
        method: 'POST',
        token,
        body: payload
      });
    }

    return mockDbtModelApi.run(payload);
  },

  streamRun(
    token: string | null | undefined,
    payload: RunDbtModelPayload,
    onEvent?: (event: DbtSingleRunStreamEvent) => void
  ): Promise<ApiResult<RunDbtModelResult>> {
    if (useHttpApiMode()) {
      return streamSingleRunHttp(token, payload, onEvent);
    }

    return mockDbtModelApi.streamRun(payload, onEvent);
  },

  runBatch(token: string | null | undefined, payload: RunDbtModelsByScopePayload): Promise<ApiResult<RunDbtModelsByScopeResult>> {
    if (useHttpApiMode()) {
      return requestApi<RunDbtModelsByScopeResult>('/users/me/dbt-models/run-batch', {
        method: 'POST',
        token,
        body: payload
      });
    }

    return mockDbtModelApi.runBatch(payload);
  },

  streamRunBatch(
    token: string | null | undefined,
    payload: RunDbtModelsByScopePayload,
    onEvent?: (event: DbtBatchRunStreamEvent) => void
  ): Promise<ApiResult<RunDbtModelsByScopeResult>> {
    if (useHttpApiMode()) {
      return streamBatchRunHttp(token, payload, onEvent);
    }

    return mockDbtModelApi.streamRunBatch(payload, onEvent);
  },

  listRuns(token: string | null | undefined, payload: ListDbtRunHistoryPayload): Promise<ApiResult<DbtRunHistoryListResponse>> {
    if (useHttpApiMode()) {
      return requestApi<DbtRunHistoryListResponse>(buildDbtRunHistoryPath(payload), {
        token
      });
    }

    return mockDbtModelApi.listRuns(payload);
  },

  getRunDetail(token: string | null | undefined, runId: string): Promise<ApiResult<DbtRunHistoryDetail>> {
    if (useHttpApiMode()) {
      return requestApi<DbtRunHistoryDetail>(`/users/me/dbt-model-runs/${encodeURIComponent(runId)}`, {
        token
      });
    }

    return mockDbtModelApi.getRunDetail(runId);
  },

  listRunModels(token: string | null | undefined, runId: string): Promise<ApiResult<DbtRunModelHistoryResponse>> {
    if (useHttpApiMode()) {
      return requestApi<DbtRunModelHistoryResponse>(`/users/me/dbt-model-runs/${encodeURIComponent(runId)}/models`, {
        token
      });
    }

    return mockDbtModelApi.listRunModels(runId);
  }
};

import type { ApiResult } from '~/types/auth';
import type {
  DbtModelListResponse,
  DbtRunHistoryDetail,
  DbtRunHistoryListResponse,
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

const buildDbtRunHistoryPath = (payload: ListDbtRunHistoryPayload) => {
  const params = new URLSearchParams();
  params.set('page', String(payload.page ?? 1));
  params.set('pageSize', String(payload.pageSize ?? 10));
  if (payload.search?.trim()) {
    params.set('search', payload.search.trim());
  }

  return `/users/me/dbt-model-runs?${params.toString()}`;
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
  }
};

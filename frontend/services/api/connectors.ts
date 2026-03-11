import type { ApiResult } from '~/types/auth';
import type {
  ConnectorRecord,
  CreateSyncJobPayload,
  ListSyncJobsPayload,
  SaveConnectorPayload,
  SyncJobListItem,
  SyncJobListResponse,
  SyncJobRecord,
  TestConnectorPayload,
  UpdateConnectorStatusPayload
} from '~/types/connectors';
import { requestApi, useHttpApiMode } from '~/services/api/http';
import { mockConnectorApi } from '~/services/mock/connectors';

const SHANGHAI_OFFSET_MS = 8 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;

const MOCK_SYNC_JOB_ITEMS: SyncJobListItem[] = [
  {
    jobId: 'ed0bd727-9af6-4c84-8f40-511d7eecb67b',
    connectorId: 'garmin-connect',
    connectorName: 'Garmin Connect',
    domain: 'health',
    status: 'success',
    triggerType: 'scheduled',
    windowStart: '2026-03-02 02:00:00',
    windowEnd: '2026-03-05 02:00:00',
    startedAt: '2026-03-05 02:00:02',
    finishedAt: '2026-03-05 02:01:11',
    fetchedCount: 145,
    insertedCount: 103,
    updatedCount: 17,
    dedupedCount: 25,
    errorMessage: null,
    createdAt: '2026-03-05 02:00:00'
  },
  {
    jobId: '6a64cf6c-b525-43d9-bd4e-d3a7e87fc26a',
    connectorId: 'garmin-connect',
    connectorName: 'Garmin Connect',
    domain: 'health',
    status: 'success',
    triggerType: 'manual',
    windowStart: '2026-03-01 00:00:00',
    windowEnd: '2026-03-04 00:00:00',
    startedAt: '2026-03-04 09:12:41',
    finishedAt: '2026-03-04 09:13:28',
    fetchedCount: 110,
    insertedCount: 79,
    updatedCount: 14,
    dedupedCount: 17,
    errorMessage: null,
    createdAt: '2026-03-04 09:12:41'
  },
  {
    jobId: '4fe96c2d-f505-4570-a715-34b091d425e6',
    connectorId: 'garmin-connect',
    connectorName: 'Garmin Connect',
    domain: 'health',
    status: 'running',
    triggerType: 'manual',
    windowStart: '2026-03-04 00:00:00',
    windowEnd: '2026-03-07 00:00:00',
    startedAt: '2026-03-09 10:02:16',
    finishedAt: null,
    fetchedCount: 128,
    insertedCount: 84,
    updatedCount: 12,
    dedupedCount: 32,
    errorMessage: null,
    createdAt: '2026-03-09 10:02:16'
  },
  {
    jobId: '81df4707-bf30-4a76-b078-e72f66ef8a8a',
    connectorId: 'garmin-connect',
    connectorName: 'Garmin Connect',
    domain: 'health',
    status: 'queued',
    triggerType: 'manual',
    windowStart: '2026-03-06 00:00:00',
    windowEnd: '2026-03-09 00:00:00',
    startedAt: null,
    finishedAt: null,
    fetchedCount: 0,
    insertedCount: 0,
    updatedCount: 0,
    dedupedCount: 0,
    errorMessage: null,
    createdAt: '2026-03-09 11:00:00'
  },
  {
    jobId: '3ae5afef-a776-4d9a-a47a-6d26879f990d',
    connectorId: 'garmin-connect',
    connectorName: 'Garmin Connect',
    domain: 'health',
    status: 'failed',
    triggerType: 'scheduled',
    windowStart: '2026-02-27 02:00:00',
    windowEnd: '2026-03-02 02:00:00',
    startedAt: '2026-03-02 02:00:05',
    finishedAt: '2026-03-02 02:00:37',
    fetchedCount: 16,
    insertedCount: 0,
    updatedCount: 0,
    dedupedCount: 0,
    errorMessage: 'Garmin Connect authentication failed.',
    createdAt: '2026-03-02 02:00:05'
  }
];

const getShanghaiTodayStart = () => {
  const now = new Date();
  const shanghaiNow = new Date(now.getTime() + SHANGHAI_OFFSET_MS);

  return new Date(
    Date.UTC(shanghaiNow.getUTCFullYear(), shanghaiNow.getUTCMonth(), shanghaiNow.getUTCDate(), 0, 0, 0) - SHANGHAI_OFFSET_MS
  );
};

const parseShanghaiDateTime = (value: string | null) => {
  if (!value) {
    return null;
  }

  const parsed = new Date(value.replace(' ', 'T') + '+08:00');
  return Number.isNaN(parsed.getTime()) ? null : parsed;
};

const inPeriodRange = (startedAt: string | null, period: ListSyncJobsPayload['period']) => {
  if (!period) {
    return true;
  }

  const startedAtDate = parseShanghaiDateTime(startedAt);
  if (!startedAtDate) {
    return false;
  }

  const now = new Date();
  const todayStart = getShanghaiTodayStart();

  if (period === 'yesterday') {
    const start = new Date(todayStart.getTime() - DAY_MS);
    const end = todayStart;
    return startedAtDate >= start && startedAtDate < end;
  }

  if (period === 'last_7_days') {
    const start = new Date(todayStart.getTime() - (6 * DAY_MS));
    return startedAtDate >= start && startedAtDate <= now;
  }

  const start = new Date(todayStart.getTime() - (29 * DAY_MS));
  return startedAtDate >= start && startedAtDate <= now;
};

const buildSyncJobsPath = (payload: ListSyncJobsPayload) => {
  const params = new URLSearchParams();
  if (payload.page != null) {
    params.set('page', String(payload.page));
  }
  if (payload.pageSize != null) {
    params.set('pageSize', String(payload.pageSize));
  }
  if (payload.search?.trim()) {
    params.set('search', payload.search.trim());
  }
  if (payload.period) {
    params.set('period', payload.period);
  }
  if (payload.status) {
    params.set('status', payload.status);
  }
  if (payload.triggerType) {
    params.set('triggerType', payload.triggerType);
  }
  if (payload.domain) {
    params.set('domain', payload.domain);
  }
  if (payload.sortBy) {
    params.set('sortBy', payload.sortBy);
  }
  if (payload.sortOrder) {
    params.set('sortOrder', payload.sortOrder);
  }

  const queryString = params.toString();
  return queryString ? `/users/me/sync-jobs?${queryString}` : '/users/me/sync-jobs';
};

const buildMockSyncJobList = (payload: ListSyncJobsPayload): SyncJobListResponse => {
  const page = payload.page ?? 1;
  const pageSize = payload.pageSize ?? 20;
  const normalizedSearch = payload.search?.trim().toLowerCase() ?? '';

  const searchMatchedAllPeriods = MOCK_SYNC_JOB_ITEMS.filter((item) => {
    return !normalizedSearch || item.connectorName.toLowerCase().includes(normalizedSearch);
  });
  const periodMatched = MOCK_SYNC_JOB_ITEMS.filter((item) => inPeriodRange(item.startedAt, payload.period));
  const searchMatched = periodMatched.filter((item) => {
    return !normalizedSearch || item.connectorName.toLowerCase().includes(normalizedSearch);
  });

  const statusFacetBase = searchMatched.filter((item) => {
    return (!payload.triggerType || item.triggerType === payload.triggerType)
      && (!payload.domain || item.domain === payload.domain);
  });
  const triggerTypeFacetBase = searchMatched.filter((item) => {
    return (!payload.status || item.status === payload.status)
      && (!payload.domain || item.domain === payload.domain);
  });
  const domainFacetBase = searchMatched.filter((item) => {
    return (!payload.status || item.status === payload.status)
      && (!payload.triggerType || item.triggerType === payload.triggerType);
  });
  const periodFacetBase = searchMatchedAllPeriods.filter((item) => {
    return (!payload.status || item.status === payload.status)
      && (!payload.triggerType || item.triggerType === payload.triggerType)
      && (!payload.domain || item.domain === payload.domain);
  });

  const filtered = searchMatched.filter((item) => {
    return (!payload.status || item.status === payload.status)
      && (!payload.triggerType || item.triggerType === payload.triggerType)
      && (!payload.domain || item.domain === payload.domain);
  });

  const total = filtered.length;
  const totalPages = total === 0 ? 0 : Math.ceil(total / pageSize);
  const from = (page - 1) * pageSize;
  const to = from + pageSize;

  return {
    items: filtered.slice(from, to),
    page: {
      page,
      pageSize,
      total,
      totalPages
    },
    facets: {
      allTasks: searchMatched.length,
      status: {
        queued: statusFacetBase.filter((item) => item.status === 'queued').length,
        running: statusFacetBase.filter((item) => item.status === 'running').length,
        success: statusFacetBase.filter((item) => item.status === 'success').length,
        failed: statusFacetBase.filter((item) => item.status === 'failed').length
      },
      triggerType: {
        manual: triggerTypeFacetBase.filter((item) => item.triggerType === 'manual').length,
        scheduled: triggerTypeFacetBase.filter((item) => item.triggerType === 'scheduled').length
      },
      domain: {
        health: domainFacetBase.filter((item) => item.domain === 'health').length,
        finance: domainFacetBase.filter((item) => item.domain === 'finance').length
      },
      period: {
        yesterday: periodFacetBase.filter((item) => inPeriodRange(item.startedAt, 'yesterday')).length,
        last7Days: periodFacetBase.filter((item) => inPeriodRange(item.startedAt, 'last_7_days')).length,
        last30Days: periodFacetBase.filter((item) => inPeriodRange(item.startedAt, 'last_30_days')).length
      }
    }
  };
};

export const connectorApi = {
  list(token?: string | null): Promise<ApiResult<ConnectorRecord[]>> {
    if (useHttpApiMode()) {
      return requestApi<ConnectorRecord[]>('/users/me/connectors', {
        token
      });
    }

    return mockConnectorApi.list();
  },

  testConnection(token: string | null | undefined, payload: TestConnectorPayload): Promise<ApiResult<null>> {
    if (useHttpApiMode()) {
      return requestApi<null>(`/users/me/connectors/${payload.id}/connection-test`, {
        method: 'POST',
        token,
        body: {
          config: payload.config
        }
      });
    }

    return mockConnectorApi.testConnection(payload);
  },

  saveConfiguration(token: string | null | undefined, payload: SaveConnectorPayload): Promise<ApiResult<ConnectorRecord>> {
    if (useHttpApiMode()) {
      return requestApi<ConnectorRecord>(`/users/me/connectors/${payload.id}/configuration`, {
        method: 'PUT',
        token,
        body: {
          schedule: payload.schedule,
          config: payload.config
        }
      });
    }

    return mockConnectorApi.saveConfiguration(payload);
  },

  updateStatus(token: string | null | undefined, payload: UpdateConnectorStatusPayload): Promise<ApiResult<ConnectorRecord>> {
    if (useHttpApiMode()) {
      return requestApi<ConnectorRecord>(`/users/me/connectors/${payload.id}/status`, {
        method: 'PATCH',
        token,
        body: {
          status: payload.status
        }
      });
    }

    return mockConnectorApi.updateStatus(payload);
  },

  createSyncJob(token: string | null | undefined, payload: CreateSyncJobPayload): Promise<ApiResult<SyncJobRecord>> {
    if (useHttpApiMode()) {
      return requestApi<SyncJobRecord>(`/users/me/connectors/${payload.id}/sync-jobs`, {
        method: 'POST',
        token,
        body: {
          startAt: payload.startAt,
          endAt: payload.endAt
        }
      });
    }

    return Promise.resolve({
      success: true,
      data: {
        jobId: `mock-${Date.now()}`,
        connectorId: payload.id,
        status: 'queued',
        triggerType: 'manual',
        startAt: payload.startAt,
        endAt: payload.endAt,
        startedAt: '',
        finishedAt: '',
        fetchedCount: 0,
        insertedCount: 0,
        updatedCount: 0,
        unchangedCount: 0,
        dedupedCount: 0,
        createdAt: payload.startAt
      },
      message: 'Garmin Connect sync job queued.'
    });
  },

  listSyncJobs(token: string | null | undefined, payload: ListSyncJobsPayload): Promise<ApiResult<SyncJobListResponse>> {
    if (useHttpApiMode()) {
      return requestApi<SyncJobListResponse>(buildSyncJobsPath(payload), {
        token
      });
    }

    return Promise.resolve({
      success: true,
      data: buildMockSyncJobList(payload)
    });
  }
};

import type { ApiResult } from '~/types/auth';
import type {
  ConnectorRecord,
  CreateSyncJobPayload,
  SaveConnectorPayload,
  SyncJobRecord,
  TestConnectorPayload,
  UpdateConnectorStatusPayload
} from '~/types/connectors';
import { requestApi, useHttpApiMode } from '~/services/api/http';
import { mockConnectorApi } from '~/services/mock/connectors';

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
  }
};

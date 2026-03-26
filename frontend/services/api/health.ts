import type { ApiResult } from '~/types/auth';
import type {
  HealthActivityDetail,
  HealthActivityListResponse,
  HealthDashboardSummary,
  ListHealthActivitiesPayload
} from '~/types/health';
import { requestApi, useHttpApiMode } from '~/services/api/http';
import { mockHealthApi } from '~/services/mock/health';

const buildActivitiesPath = (payload: ListHealthActivitiesPayload) => {
  const params = new URLSearchParams();
  params.set('page', String(payload.page ?? 1));
  params.set('pageSize', String(payload.pageSize ?? 10));
  if (payload.search?.trim()) {
    params.set('search', payload.search.trim());
  }
  return `/users/me/health-dashboard/activities?${params.toString()}`;
};

export const healthApi = {
  getSummary(token: string | null | undefined): Promise<ApiResult<HealthDashboardSummary | null>> {
    if (useHttpApiMode()) {
      return requestApi<HealthDashboardSummary | null>('/users/me/health-dashboard/summary', {
        token
      });
    }

    return mockHealthApi.getSummary();
  },

  listActivities(
    token: string | null | undefined,
    payload: ListHealthActivitiesPayload
  ): Promise<ApiResult<HealthActivityListResponse>> {
    if (useHttpApiMode()) {
      return requestApi<HealthActivityListResponse>(buildActivitiesPath(payload), {
        token
      });
    }

    return mockHealthApi.listActivities(payload);
  },

  getActivityDetail(
    token: string | null | undefined,
    activityRecordId: string
  ): Promise<ApiResult<HealthActivityDetail>> {
    if (useHttpApiMode()) {
      return requestApi<HealthActivityDetail>(`/users/me/health-dashboard/activities/${encodeURIComponent(activityRecordId)}`, {
        token
      });
    }

    return mockHealthApi.getActivityDetail(activityRecordId);
  }
};

import type { ApiResult } from '~/types/auth';
import type {
  HealthActivityDetail,
  HealthActivityListResponse,
  HealthCaloriesCardResponse,
  HealthDashboardSummary,
  HealthHeartRateCardResponse,
  HealthStressCardResponse,
  HealthWeightCardResponse,
  ListHealthActivitiesPayload
} from '~/types/health';
import { requestApi, useHttpApiMode } from '~/services/api/http';
import { mockHealthApi } from '~/services/mock/health';

const buildCardPath = (resource: 'heart-rate' | 'weight' | 'calories' | 'stress', date?: string | null) => {
  if (!date) {
    return `/users/me/health-dashboard/${resource}`;
  }

  const params = new URLSearchParams();
  params.set('date', date);
  return `/users/me/health-dashboard/${resource}?${params.toString()}`;
};

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
  getSummary(): Promise<ApiResult<HealthDashboardSummary | null>> {
    if (useHttpApiMode()) {
      return requestApi<HealthDashboardSummary | null>('/users/me/health-dashboard/summary');
    }

    return mockHealthApi.getSummary();
  },

  getHeartRateCard(date?: string | null): Promise<ApiResult<HealthHeartRateCardResponse>> {
    if (useHttpApiMode()) {
      return requestApi<HealthHeartRateCardResponse>(buildCardPath('heart-rate', date));
    }

    return mockHealthApi.getHeartRateCard(date);
  },

  getWeightCard(date?: string | null): Promise<ApiResult<HealthWeightCardResponse>> {
    if (useHttpApiMode()) {
      return requestApi<HealthWeightCardResponse>(buildCardPath('weight', date));
    }

    return mockHealthApi.getWeightCard(date);
  },

  getCaloriesCard(date?: string | null): Promise<ApiResult<HealthCaloriesCardResponse>> {
    if (useHttpApiMode()) {
      return requestApi<HealthCaloriesCardResponse>(buildCardPath('calories', date));
    }

    return mockHealthApi.getCaloriesCard(date);
  },

  getStressCard(date?: string | null): Promise<ApiResult<HealthStressCardResponse>> {
    if (useHttpApiMode()) {
      return requestApi<HealthStressCardResponse>(buildCardPath('stress', date));
    }

    return mockHealthApi.getStressCard(date);
  },

  listActivities(payload: ListHealthActivitiesPayload): Promise<ApiResult<HealthActivityListResponse>> {
    if (useHttpApiMode()) {
      return requestApi<HealthActivityListResponse>(buildActivitiesPath(payload));
    }

    return mockHealthApi.listActivities(payload);
  },

  getActivityDetail(activityRecordId: string): Promise<ApiResult<HealthActivityDetail>> {
    if (useHttpApiMode()) {
      return requestApi<HealthActivityDetail>(`/users/me/health-dashboard/activities/${encodeURIComponent(activityRecordId)}`);
    }

    return mockHealthApi.getActivityDetail(activityRecordId);
  }
};

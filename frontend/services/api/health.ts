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
  getSummary(token: string | null | undefined): Promise<ApiResult<HealthDashboardSummary | null>> {
    if (useHttpApiMode()) {
      return requestApi<HealthDashboardSummary | null>('/users/me/health-dashboard/summary', {
        token
      });
    }

    return mockHealthApi.getSummary();
  },

  getHeartRateCard(
    token: string | null | undefined,
    date?: string | null
  ): Promise<ApiResult<HealthHeartRateCardResponse>> {
    if (useHttpApiMode()) {
      return requestApi<HealthHeartRateCardResponse>(buildCardPath('heart-rate', date), { token });
    }

    return mockHealthApi.getHeartRateCard(date);
  },

  getWeightCard(
    token: string | null | undefined,
    date?: string | null
  ): Promise<ApiResult<HealthWeightCardResponse>> {
    if (useHttpApiMode()) {
      return requestApi<HealthWeightCardResponse>(buildCardPath('weight', date), { token });
    }

    return mockHealthApi.getWeightCard(date);
  },

  getCaloriesCard(
    token: string | null | undefined,
    date?: string | null
  ): Promise<ApiResult<HealthCaloriesCardResponse>> {
    if (useHttpApiMode()) {
      return requestApi<HealthCaloriesCardResponse>(buildCardPath('calories', date), { token });
    }

    return mockHealthApi.getCaloriesCard(date);
  },

  getStressCard(
    token: string | null | undefined,
    date?: string | null
  ): Promise<ApiResult<HealthStressCardResponse>> {
    if (useHttpApiMode()) {
      return requestApi<HealthStressCardResponse>(buildCardPath('stress', date), { token });
    }

    return mockHealthApi.getStressCard(date);
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

import type { ApiResult } from '~/types/auth';
import type {
  HealthActivityDetail,
  HealthActivityListItem,
  HealthActivityListResponse,
  HealthCaloriesCard,
  HealthCaloriesCardResponse,
  HealthDashboardSummary,
  HealthHeartRateCard,
  HealthHeartRateCardResponse,
  HealthStressCard,
  HealthStressCardResponse,
  HealthWeightCard,
  HealthWeightCardResponse,
  ListHealthActivitiesPayload
} from '~/types/health';

const wait = (ms = 200) => new Promise((resolve) => setTimeout(resolve, ms));

const mockSummary: HealthDashboardSummary = {
  summaryDate: '2026-03-19',
  heartRate: {
    highest: 114,
    resting: 58,
    average: 97
  },
  weight: {
    weightKg: 68.2,
    bmi: 22.4,
    previousWeightKg: 67.4,
    weightDeltaKg: 0.8,
    weightDeltaPercent: 1.19
  },
  calories: {
    restingBurn: 1411,
    activeBurn: 157
  },
  stress: {
    overall: 24,
    lowDurationSeconds: 13800,
    mediumDurationSeconds: 8400,
    highDurationSeconds: 1260,
    restDurationSeconds: 7200
  }
};

const mockHeartRateCardsByDate: Record<string, HealthHeartRateCard | null> = {
  '2026-03-19': mockSummary.heartRate,
  '2026-03-18': {
    highest: 134,
    resting: 69,
    average: 87
  },
  '2026-03-17': null
};

const mockWeightCardsByDate: Record<string, HealthWeightCard | null> = {
  '2026-03-19': mockSummary.weight,
  '2026-03-18': {
    weightKg: 67.4,
    bmi: 22.1,
    previousWeightKg: 67.8,
    weightDeltaKg: -0.4,
    weightDeltaPercent: -0.59
  },
  '2026-03-17': null
};

const mockCaloriesCardsByDate: Record<string, HealthCaloriesCard | null> = {
  '2026-03-19': mockSummary.calories,
  '2026-03-18': {
    restingBurn: 1406,
    activeBurn: 221
  },
  '2026-03-17': null
};

const mockStressCardsByDate: Record<string, HealthStressCard | null> = {
  '2026-03-19': mockSummary.stress,
  '2026-03-18': {
    overall: 31,
    lowDurationSeconds: 11220,
    mediumDurationSeconds: 9540,
    highDurationSeconds: 2100,
    restDurationSeconds: 6480
  },
  '2026-03-17': null
};

const findLatestCardEntry = <T>(cardsByDate: Record<string, T | null>) => {
  const latestEntry = Object.entries(cardsByDate)
    .filter(([, data]) => data != null)
    .sort(([left], [right]) => right.localeCompare(left))[0];

  if (!latestEntry) {
    return {
      date: null,
      data: null
    };
  }

  return {
    date: latestEntry[0],
    data: latestEntry[1]
  };
};

const buildCardResponse = <T>(date: string | null, data: T | null) => ({
  success: true as const,
  data: {
    date,
    data
  }
});

const mockActivities: HealthActivityListItem[] = [
  {
    activityRecordId: '4cb0c0e2-4a73-4332-a2cb-a8320d77d4e1',
    activityName: '室内骑行',
    activityType: 'indoor_cycling',
    startTime: '2026-01-27 20:53:16',
    endTime: '2026-01-27 21:33:01',
    durationSeconds: 2385.79,
    calories: 357,
    avgHeartRate: 128,
    maxHeartRate: 156
  },
  {
    activityRecordId: '073be0e6-d4fd-4d0f-bfd3-572b8f740e11',
    activityName: '室内骑行',
    activityType: 'indoor_cycling',
    startTime: '2026-01-24 19:59:38',
    endTime: '2026-01-24 20:45:35',
    durationSeconds: 2757.35,
    calories: 460,
    avgHeartRate: 135,
    maxHeartRate: 171
  },
  {
    activityRecordId: '42173093-7316-4a84-b38b-21d4b5dd8f17',
    activityName: '室内骑行',
    activityType: 'indoor_cycling',
    startTime: '2026-01-21 20:02:46',
    endTime: '2026-01-21 20:38:10',
    durationSeconds: 2123.67,
    calories: 359,
    avgHeartRate: 133,
    maxHeartRate: 165
  },
  {
    activityRecordId: 'd784f7e3-7558-41f3-b3b3-04e27f86d119',
    activityName: '室内骑行',
    activityType: 'indoor_cycling',
    startTime: '2026-01-19 20:15:11',
    endTime: '2026-01-19 20:49:49',
    durationSeconds: 2078.32,
    calories: 356,
    avgHeartRate: 134,
    maxHeartRate: 157
  },
  {
    activityRecordId: 'adb390b1-8fe7-4ed2-b4a1-2381557ed4e7',
    activityName: '成都市 公路骑行',
    activityType: 'road_biking',
    startTime: '2025-05-19 00:16:22',
    endTime: '2025-05-19 00:33:04',
    durationSeconds: 1001.71,
    calories: 212,
    avgHeartRate: 149,
    maxHeartRate: 169
  }
];

const mockActivityDetails: Record<string, HealthActivityDetail> = {
  '4cb0c0e2-4a73-4332-a2cb-a8320d77d4e1': {
    activityRecordId: '4cb0c0e2-4a73-4332-a2cb-a8320d77d4e1',
    activityName: '室内骑行',
    activityType: 'indoor_cycling',
    basics: [
      { label: 'Activity Name', value: '室内骑行' },
      { label: 'Activity Type', value: 'Indoor cycling' },
      { label: 'Source Date', value: '2026-01-27' },
      { label: 'Owner', value: 'Fang Yongchao' },
      { label: 'Privacy', value: 'Private' }
    ],
    performance: [
      { label: 'Duration', value: '39m 46s' },
      { label: 'Moving Duration', value: '39m 46s' },
      { label: 'Elapsed Duration', value: '39m 46s' },
      { label: 'Distance', value: '19.80 km' },
      { label: 'Calories', value: '357 kcal' },
      { label: 'Avg Speed', value: '29.9 km/h' },
      { label: 'Max Speed', value: '42.6 km/h' },
      { label: 'Training Effect', value: 'Maintaining' }
    ],
    heartRate: [
      { label: 'Average HR', value: '128 bpm' },
      { label: 'Max HR', value: '156 bpm' },
      { label: 'Zone 1', value: '4m 30s' },
      { label: 'Zone 2', value: '12m 10s' },
      { label: 'Zone 3', value: '16m 48s' },
      { label: 'Zone 4', value: '6m 18s' }
    ],
    locationAndTiming: [
      { label: 'Start Time', value: '2026-01-27 20:53:16' },
      { label: 'End Time', value: '2026-01-27 21:33:01' },
      { label: 'Start Time (Local)', value: '2026-01-27T20:53:16+08:00' },
      { label: 'Time Zone', value: 'Asia/Shanghai' },
      { label: 'Location', value: 'Chengdu' }
    ]
  }
};

const paginate = (items: HealthActivityListItem[], payload: ListHealthActivitiesPayload): HealthActivityListResponse => {
  const page = payload.page ?? 1;
  const pageSize = payload.pageSize ?? 10;
  const normalizedSearch = payload.search?.trim().toLowerCase() ?? '';
  const filteredItems = normalizedSearch
    ? items.filter((item) => (item.activityName ?? '').toLowerCase().includes(normalizedSearch))
    : items;
  const first = (page - 1) * pageSize;
  const pagedItems = filteredItems.slice(first, first + pageSize);
  const total = filteredItems.length;
  return {
    items: pagedItems,
    page: {
      page,
      pageSize,
      total,
      totalPages: total === 0 ? 0 : Math.ceil(total / pageSize)
    }
  };
};

export const mockHealthApi = {
  async getSummary(): Promise<ApiResult<HealthDashboardSummary | null>> {
    await wait();
    return {
      success: true,
      data: mockSummary
    };
  },

  async getHeartRateCard(date?: string | null): Promise<ApiResult<HealthHeartRateCardResponse>> {
    await wait();
    if (!date) {
      const latest = findLatestCardEntry(mockHeartRateCardsByDate);
      return buildCardResponse(latest.date, latest.data);
    }

    return buildCardResponse(date, mockHeartRateCardsByDate[date] ?? null);
  },

  async getWeightCard(date?: string | null): Promise<ApiResult<HealthWeightCardResponse>> {
    await wait();
    if (!date) {
      const latest = findLatestCardEntry(mockWeightCardsByDate);
      return buildCardResponse(latest.date, latest.data);
    }

    return buildCardResponse(date, mockWeightCardsByDate[date] ?? null);
  },

  async getCaloriesCard(date?: string | null): Promise<ApiResult<HealthCaloriesCardResponse>> {
    await wait();
    if (!date) {
      const latest = findLatestCardEntry(mockCaloriesCardsByDate);
      return buildCardResponse(latest.date, latest.data);
    }

    return buildCardResponse(date, mockCaloriesCardsByDate[date] ?? null);
  },

  async getStressCard(date?: string | null): Promise<ApiResult<HealthStressCardResponse>> {
    await wait();
    if (!date) {
      const latest = findLatestCardEntry(mockStressCardsByDate);
      return buildCardResponse(latest.date, latest.data);
    }

    return buildCardResponse(date, mockStressCardsByDate[date] ?? null);
  },

  async listActivities(payload: ListHealthActivitiesPayload): Promise<ApiResult<HealthActivityListResponse>> {
    await wait();
    return {
      success: true,
      data: paginate(mockActivities, payload)
    };
  },

  async getActivityDetail(activityRecordId: string): Promise<ApiResult<HealthActivityDetail>> {
    await wait();
    const detail = mockActivityDetails[activityRecordId];
    if (!detail) {
      return {
        success: false,
        message: 'Activity not found.',
        code: 'HEALTH_ACTIVITY_NOT_FOUND'
      };
    }

    return {
      success: true,
      data: detail
    };
  }
};

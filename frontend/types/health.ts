export type HealthDashboardSummary = {
  summaryDate: string | null;
  heartRate: HealthHeartRateCard;
  weight: HealthWeightCard;
  calories: HealthCaloriesCard;
  stress: HealthStressCard;
};

export type HealthHeartRateCard = {
  highest: number | null;
  resting: number | null;
  average: number | null;
};

export type HealthWeightCard = {
  weightKg: number | null;
  bmi: number | null;
  previousWeightKg: number | null;
  weightDeltaKg: number | null;
  weightDeltaPercent: number | null;
};

export type HealthCaloriesCard = {
  restingBurn: number | null;
  activeBurn: number | null;
};

export type HealthStressCard = {
  overall: number | null;
  lowDurationSeconds: number | null;
  mediumDurationSeconds: number | null;
  highDurationSeconds: number | null;
  restDurationSeconds: number | null;
};

export type HealthHeartRateCardResponse = {
  date: string | null;
  data: HealthHeartRateCard | null;
};

export type HealthWeightCardResponse = {
  date: string | null;
  data: HealthWeightCard | null;
};

export type HealthCaloriesCardResponse = {
  date: string | null;
  data: HealthCaloriesCard | null;
};

export type HealthStressCardResponse = {
  date: string | null;
  data: HealthStressCard | null;
};

export type HealthActivityListItem = {
  activityRecordId: string;
  activityName: string | null;
  activityType: string | null;
  startTime: string | null;
  endTime: string | null;
  durationSeconds: number | null;
  calories: number | null;
  avgHeartRate: number | null;
  maxHeartRate: number | null;
};

export type HealthActivityPage = {
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
};

export type HealthActivityListResponse = {
  items: HealthActivityListItem[];
  page: HealthActivityPage;
};

export type ListHealthActivitiesPayload = {
  page?: number;
  pageSize?: number;
  search?: string;
};

export type HealthDetailField = {
  label: string;
  value: string;
};

export type HealthActivityDetail = {
  activityRecordId: string;
  activityName: string | null;
  activityType: string | null;
  basics: HealthDetailField[];
  performance: HealthDetailField[];
  heartRate: HealthDetailField[];
  locationAndTiming: HealthDetailField[];
};

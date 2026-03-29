<script setup lang="ts">
import { healthApi } from '~/services/api/health';
import { useAuthStore } from '~/stores/auth';
import type {
  HealthActivityDetail,
  HealthActivityListItem,
  HealthCaloriesCard,
  HealthDashboardSummary,
  HealthHeartRateCard,
  HealthStressCard,
  HealthWeightCard
} from '~/types/health';

type DataTablePageEvent = {
  page: number;
  rows: number;
  first: number;
};

type ActivityDetailSection = {
  key: string;
  title: string;
  items: Array<{
    label: string;
    value: string;
  }>;
};

type SummaryCardState<T> = {
  selectedDate: Date | null;
  data: T | null;
  loading: boolean;
  error: string;
};

type SummaryCardKey = 'heart-rate' | 'weight' | 'stress';
type HeartRateZoneKey = 'rest' | 'warmup' | 'fat-burn' | 'aerobic' | 'threshold' | 'anaerobic';
type SummaryDateCardKey = 'heart-rate' | 'weight' | 'calories' | 'stress';
type DatePickerValue = Date | Date[] | (Date | null)[] | null | undefined;

type SummaryBreakdownItem = {
  key: string;
  label: string;
  value: number | null;
  share: number | null;
  barClass: string;
  width?: string;
  height?: string;
};

type SummaryHoverInfo = {
  label: string;
  value: string;
  meta?: string;
};

type HeartRateZone = {
  key: HeartRateZoneKey;
  label: string;
  min: number;
  max: number;
  colorClass: string;
};

type StressRingSegment = SummaryBreakdownItem & {
  colorClass: string;
  path: string;
};

const DEFAULT_PAGE_SIZE = 10;
const APP_TIME_ZONE = 'Asia/Shanghai';

const auth = useAuthStore();
const { showToast } = useAppToast();

const summary = ref<HealthDashboardSummary | null>(null);
const summaryLoading = ref(false);
const summaryError = ref('');
const heartRateCard = reactive<SummaryCardState<HealthHeartRateCard>>({
  selectedDate: null,
  data: null,
  loading: false,
  error: ''
});
const weightCard = reactive<SummaryCardState<HealthWeightCard>>({
  selectedDate: null,
  data: null,
  loading: false,
  error: ''
});
const caloriesCard = reactive<SummaryCardState<HealthCaloriesCard>>({
  selectedDate: null,
  data: null,
  loading: false,
  error: ''
});
const stressCard = reactive<SummaryCardState<HealthStressCard>>({
  selectedDate: null,
  data: null,
  loading: false,
  error: ''
});

const activities = ref<HealthActivityListItem[]>([]);
const activitiesLoading = ref(false);
const activitiesError = ref('');
const activitySearchInput = ref('');
const activityPage = reactive({
  page: 1,
  pageSize: DEFAULT_PAGE_SIZE,
  total: 0,
  totalPages: 0
});

const detailDialogVisible = ref(false);
const detailLoading = ref(false);
const detailError = ref('');
const selectedActivity = ref<HealthActivityListItem | null>(null);
const activityDetail = ref<HealthActivityDetail | null>(null);
const activityDetailCache = reactive<Record<string, HealthActivityDetail>>({});
const supportsHoverInteraction = ref(false);
const hoveredCalorieTarget = ref<string | null>(null);
const hoveredSummaryTargets = reactive<Record<SummaryCardKey, string | null>>({
  'heart-rate': null,
  weight: null,
  stress: null
});

let summaryRequestId = 0;
let activityRequestId = 0;
let detailRequestId = 0;
let heartRateCardRequestId = 0;
let weightCardRequestId = 0;
let caloriesCardRequestId = 0;
let stressCardRequestId = 0;
let activitySearchTimer: ReturnType<typeof setTimeout> | null = null;

const HEART_RATE_MIN = 20;
const HEART_RATE_MAX = 200;
const HEART_RATE_RING_CENTER = 110;
const HEART_RATE_RING_RADIUS = 72;
const HEART_RATE_MARKER_RADIUS = 72;
const STRESS_RING_CENTER = 110;
const STRESS_RING_RADIUS = 78;

const heartRateZones: readonly HeartRateZone[] = [
  { key: 'rest', label: 'Resting', min: 20, max: 93, colorClass: 'text-slate-300 dark:text-slate-500' },
  { key: 'warmup', label: 'Warm Up', min: 94, max: 112, colorClass: 'text-slate-500 dark:text-slate-400' },
  { key: 'fat-burn', label: 'Fat Burn', min: 113, max: 131, colorClass: 'text-sky-500 dark:text-sky-400' },
  { key: 'aerobic', label: 'Aerobic', min: 132, max: 149, colorClass: 'text-emerald-500 dark:text-emerald-400' },
  { key: 'threshold', label: 'Threshold', min: 150, max: 168, colorClass: 'text-amber-500 dark:text-amber-400' },
  { key: 'anaerobic', label: 'Anaerobic', min: 169, max: 200, colorClass: 'text-rose-500 dark:text-rose-400' }
] as const;

const bmiBands = [
  { key: 'under', label: 'Under', description: 'Below healthy range', min: 0, max: 18.5, barClass: 'bg-slate-300 dark:bg-slate-600', leftPercent: 0, widthPercent: 17.5 },
  { key: 'normal', label: 'Normal', description: 'Healthy range', min: 18.5, max: 25, barClass: 'bg-emerald-400/70 dark:bg-emerald-400/60', leftPercent: 17.5, widthPercent: 32.5 },
  { key: 'over', label: 'Over', description: 'Elevated range', min: 25, max: 30, barClass: 'bg-amber-400/80 dark:bg-amber-400/70', leftPercent: 50, widthPercent: 25 },
  { key: 'high', label: 'High', description: 'High BMI range', min: 30, max: Infinity, barClass: 'bg-rose-500/80 dark:bg-rose-500/70', leftPercent: 75, widthPercent: 25 }
] as const;

const getDateParts = (value: Date, timeZone = APP_TIME_ZONE) => {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).formatToParts(value);

  return {
    year: Number(parts.find((part) => part.type === 'year')?.value ?? 0),
    month: Number(parts.find((part) => part.type === 'month')?.value ?? 0),
    day: Number(parts.find((part) => part.type === 'day')?.value ?? 0)
  };
};

const createLocalDate = (year: number, month: number, day: number) => {
  return new Date(year, month - 1, day, 12, 0, 0, 0);
};

const parseIsoDate = (value: string | null | undefined) => {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return null;
  }

  const [year, month, day] = value.split('-').map((part) => Number(part));
  return createLocalDate(year, month, day);
};

const formatIsoDate = (value: Date | null | undefined) => {
  if (!(value instanceof Date) || Number.isNaN(value.getTime())) {
    return null;
  }

  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const getShanghaiYesterdayDate = () => {
  const { year, month, day } = getDateParts(new Date());
  const value = createLocalDate(year, month, day);
  value.setDate(value.getDate() - 1);
  return value;
};

const dashboardMaxDate = computed(() => getShanghaiYesterdayDate());
const heartRateData = computed(() => heartRateCard.data);
const weightData = computed(() => weightCard.data);
const caloriesData = computed(() => caloriesCard.data);
const stressData = computed(() => stressCard.data);
const averageHeartRate = computed(() => heartRateData.value?.average ?? null);

const clampHeartRate = (value: number) => {
  return Math.min(HEART_RATE_MAX, Math.max(HEART_RATE_MIN, value));
};

const heartRateRangeLabel = (zone: HeartRateZone) => `${zone.min}-${zone.max} bpm`;

const findHeartRateZone = (value: number | null | undefined) => {
  if (value == null || Number.isNaN(value)) {
    return null;
  }

  const clamped = clampHeartRate(value);
  return heartRateZones.find((zone) => clamped >= zone.min && clamped <= zone.max) ?? null;
};

const activityAvgHeartRateClass = (value: number | null | undefined) => {
  const zone = findHeartRateZone(value);
  if (!zone) {
    return 'border-slate-200/80 bg-slate-50/80 text-slate-500 dark:border-slate-700 dark:bg-slate-800/60 dark:text-slate-400';
  }

  switch (zone.key) {
    case 'rest':
      return 'border-slate-200/80 bg-slate-100 text-slate-500 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300';
    case 'warmup':
      return 'border-slate-300/80 bg-slate-200/80 text-slate-700 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-100';
    case 'fat-burn':
      return 'border-sky-200/80 bg-sky-50 text-sky-600 dark:border-sky-900/80 dark:bg-sky-950/50 dark:text-sky-300';
    case 'aerobic':
      return 'border-emerald-200/80 bg-emerald-50 text-emerald-600 dark:border-emerald-900/80 dark:bg-emerald-950/40 dark:text-emerald-300';
    case 'threshold':
      return 'border-amber-200/80 bg-amber-50 text-amber-600 dark:border-amber-900/80 dark:bg-amber-950/40 dark:text-amber-300';
    case 'anaerobic':
      return 'border-rose-200/80 bg-rose-50 text-rose-600 dark:border-rose-900/80 dark:bg-rose-950/40 dark:text-rose-300';
    default:
      return 'border-slate-200/80 bg-slate-50/80 text-slate-500 dark:border-slate-700 dark:bg-slate-800/60 dark:text-slate-400';
  }
};

const heartRateValueToAngle = (value: number) => {
  const progress = (clampHeartRate(value) - HEART_RATE_MIN) / (HEART_RATE_MAX - HEART_RATE_MIN);
  return 90 + (progress * 360);
};

const polarToCartesian = (angleInDegrees: number, radius: number) => {
  const radians = (angleInDegrees * Math.PI) / 180;
  return {
    x: HEART_RATE_RING_CENTER + (radius * Math.cos(radians)),
    y: HEART_RATE_RING_CENTER + (radius * Math.sin(radians))
  };
};

const polarToCartesianAt = (angleInDegrees: number, radius: number, center: number) => {
  const radians = (angleInDegrees * Math.PI) / 180;
  return {
    x: center + (radius * Math.cos(radians)),
    y: center + (radius * Math.sin(radians))
  };
};

const describeHeartRateArc = (startValue: number, endValue: number) => {
  const startAngle = heartRateValueToAngle(startValue);
  const endAngle = heartRateValueToAngle(endValue);
  const startPoint = polarToCartesian(startAngle, HEART_RATE_RING_RADIUS);
  const endPoint = polarToCartesian(endAngle, HEART_RATE_RING_RADIUS);
  const largeArcFlag = endAngle - startAngle > 180 ? 1 : 0;

  return `M ${startPoint.x.toFixed(2)} ${startPoint.y.toFixed(2)} A ${HEART_RATE_RING_RADIUS} ${HEART_RATE_RING_RADIUS} 0 ${largeArcFlag} 1 ${endPoint.x.toFixed(2)} ${endPoint.y.toFixed(2)}`;
};

const describeArcAt = (startAngle: number, endAngle: number, radius: number, center: number) => {
  const normalizedEndAngle = endAngle <= startAngle ? startAngle + 0.001 : endAngle;
  const startPoint = polarToCartesianAt(startAngle, radius, center);
  const endPoint = polarToCartesianAt(normalizedEndAngle, radius, center);
  const largeArcFlag = normalizedEndAngle - startAngle > 180 ? 1 : 0;

  return `M ${startPoint.x.toFixed(2)} ${startPoint.y.toFixed(2)} A ${radius} ${radius} 0 ${largeArcFlag} 1 ${endPoint.x.toFixed(2)} ${endPoint.y.toFixed(2)}`;
};

const heartRateRingSegments = computed(() => {
  return heartRateZones.map((zone) => ({
    ...zone,
    path: describeHeartRateArc(zone.min, zone.max)
  }));
});

const averageHeartRateZone = computed(() => findHeartRateZone(averageHeartRate.value));

const averageHeartRateMarker = computed(() => {
  if (averageHeartRate.value == null || Number.isNaN(averageHeartRate.value)) {
    return null;
  }

  return polarToCartesian(heartRateValueToAngle(averageHeartRate.value), HEART_RATE_MARKER_RADIUS);
});

const heartRateHasData = computed(() => {
  return heartRateData.value?.highest != null || heartRateData.value?.resting != null || heartRateData.value?.average != null;
});

const weightHasData = computed(() => {
  return weightData.value?.weightKg != null || weightData.value?.bmi != null;
});

const bmiMarkerPosition = computed(() => {
  const bmi = weightData.value?.bmi;
  if (bmi == null || Number.isNaN(bmi)) {
    return null;
  }

  const min = 15;
  const max = 35;
  const clamped = Math.min(max, Math.max(min, bmi));
  return `${((clamped - min) / (max - min)) * 100}%`;
});

const bmiMarkerPercent = computed(() => {
  const bmi = weightData.value?.bmi;
  if (bmi == null || Number.isNaN(bmi)) {
    return null;
  }

  const min = 15;
  const max = 35;
  const clamped = Math.min(max, Math.max(min, bmi));
  return ((clamped - min) / (max - min)) * 100;
});

const totalCaloriesBurned = computed(() => {
  const resting = caloriesData.value?.restingBurn ?? null;
  const active = caloriesData.value?.activeBurn ?? null;

  if (resting == null && active == null) {
    return null;
  }

  return (resting ?? 0) + (active ?? 0);
});

const calorieBreakdownItems = computed<SummaryBreakdownItem[]>(() => {
  const resting = caloriesData.value?.restingBurn ?? null;
  const active = caloriesData.value?.activeBurn ?? null;
  const total = totalCaloriesBurned.value;

  return [
    {
      key: 'resting',
      label: 'Resting',
      value: resting,
      share: resting != null && total != null && total > 0 ? (resting / total) * 100 : null,
      barClass: 'bg-amber-400 dark:bg-amber-300'
    },
    {
      key: 'active',
      label: 'Active',
      value: active,
      share: active != null && total != null && total > 0 ? (active / total) * 100 : null,
      barClass: 'bg-orange-600 dark:bg-orange-400'
    }
  ];
});

const calorieSegments = computed<SummaryBreakdownItem[]>(() => {
  const total = totalCaloriesBurned.value;
  if (total == null || total <= 0) {
    return [];
  }

  return calorieBreakdownItems.value
    .filter((segment) => segment.value != null)
    .map((segment) => ({
      ...segment,
      width: `${Math.max(12, (((segment.value ?? 0) / total) * 100))}%`
    }));
});

const stressSegments = computed<SummaryBreakdownItem[]>(() => {
  if (!stressData.value) {
    return [];
  }

  const segments = [
    {
      key: 'low',
      label: 'Low',
      value: stressData.value.lowDurationSeconds,
      barClass: 'bg-emerald-400 dark:bg-emerald-300'
    },
    {
      key: 'medium',
      label: 'Medium',
      value: stressData.value.mediumDurationSeconds,
      barClass: 'bg-amber-400 dark:bg-amber-300'
    },
    {
      key: 'high',
      label: 'High',
      value: stressData.value.highDurationSeconds,
      barClass: 'bg-rose-500 dark:bg-rose-400'
    },
    {
      key: 'rest',
      label: 'Rest',
      value: stressData.value.restDurationSeconds,
      barClass: 'bg-slate-400 dark:bg-slate-500'
    }
  ].filter((segment) => segment.value != null && (segment.value ?? 0) > 0);

  const total = segments.reduce((sum, segment) => sum + (segment.value ?? 0), 0);
  if (total <= 0) {
    return [];
  }

  return segments.map((segment) => ({
    ...segment,
    share: ((segment.value ?? 0) / total) * 100,
    width: `${((segment.value ?? 0) / total) * 100}%`
  }));
});

const stressRingSegments = computed<StressRingSegment[]>(() => {
  if (stressSegments.value.length === 0) {
    return [];
  }

  let currentAngle = -90;

  return stressSegments.value.map((segment) => {
    const sweep = ((segment.share ?? 0) / 100) * 360;
    const startAngle = currentAngle;
    const endAngle = currentAngle + sweep;
    currentAngle = endAngle;

    const colorClass = segment.key === 'low'
      ? 'text-emerald-400 dark:text-emerald-300'
      : segment.key === 'medium'
        ? 'text-amber-400 dark:text-amber-300'
        : segment.key === 'high'
          ? 'text-rose-500 dark:text-rose-400'
          : 'text-slate-400 dark:text-slate-500';

    return {
      ...segment,
      colorClass,
      path: describeArcAt(startAngle, endAngle, STRESS_RING_RADIUS, STRESS_RING_CENTER)
    };
  });
});

const stressHasData = computed(() => {
  return stressData.value?.overall != null || stressSegments.value.length > 0;
});

const totalTrackedStressDuration = computed(() => {
  if (stressSegments.value.length === 0) {
    return null;
  }

  return stressSegments.value.reduce((sum, segment) => sum + (segment.value ?? 0), 0);
});

const bmiCategory = computed(() => {
  const bmi = weightData.value?.bmi;
  if (bmi == null || Number.isNaN(bmi)) {
    return null;
  }

  return bmiBands.find((band) => bmi >= band.min && bmi < band.max) ?? bmiBands[bmiBands.length - 1];
});

const hasWeightDelta = computed(() => {
  return weightData.value?.weightDeltaKg != null && weightData.value?.weightDeltaPercent != null;
});

const weightDeltaDirection = computed(() => {
  const delta = weightData.value?.weightDeltaKg;
  if (delta == null || Number.isNaN(delta) || delta === 0) {
    return 'flat';
  }

  return delta > 0 ? 'up' : 'down';
});

const weightDeltaToneClass = computed(() => {
  if (weightDeltaDirection.value === 'up') {
    return 'text-amber-600 dark:text-amber-300';
  }

  if (weightDeltaDirection.value === 'down') {
    return 'text-sky-600 dark:text-sky-300';
  }

  return 'text-slate-500 dark:text-slate-400';
});

const hoveredHeartRateInfo = computed<SummaryHoverInfo | null>(() => {
  const target = hoveredSummaryTargets['heart-rate'];
  if (!target) {
    return null;
  }

  const zone = heartRateZones.find((item) => item.key === target);
  if (!zone) {
    return null;
  }

  return {
    label: zone.label,
    value: heartRateRangeLabel(zone)
  };
});

const hoveredWeightInfo = computed<SummaryHoverInfo | null>(() => {
  const target = hoveredSummaryTargets.weight;
  if (!target) {
    return null;
  }

  if (target === 'marker') {
    return {
      label: 'Current BMI',
      value: formatValueOnly(weightData.value?.bmi, 1),
      meta: bmiCategory.value
        ? `${bmiCategory.value.label} · ${formatBmiBandRange(bmiCategory.value.min, bmiCategory.value.max)}`
        : undefined
    };
  }

  const band = bmiBands.find((item) => item.key === target);
  if (!band) {
    return null;
  }

  return {
    label: band.label,
    value: formatBmiBandRange(band.min, band.max),
    meta: bmiCategory.value?.key === band.key
      ? `${band.description} · Current BMI ${formatValueOnly(weightData.value?.bmi, 1)}`
      : band.description
  };
});

const hoveredCalorieInfo = computed<SummaryHoverInfo | null>(() => {
  const target = hoveredCalorieTarget.value;
  if (!target) {
    return null;
  }

  const segment = calorieBreakdownItems.value.find((item) => item.key === target);
  if (!segment) {
    return null;
  }

  return {
    label: segment.label,
    value: formatPercent(segment.share, 0)
  };
});

const hoveredStressInfo = computed<SummaryHoverInfo | null>(() => {
  const target = hoveredSummaryTargets.stress;
  if (!target) {
    return null;
  }

  const segment = stressSegments.value.find((item) => item.key === target);
  if (!segment) {
    return null;
  }

  return {
    label: segment.label,
    value: formatDuration(segment.value)
  };
});

const detailSections = computed<ActivityDetailSection[]>(() => {
  if (!activityDetail.value) {
    return [];
  }

  return [
    { key: 'basics', title: 'Basics', items: activityDetail.value.basics },
    { key: 'performance', title: 'Performance', items: activityDetail.value.performance },
    { key: 'heart-rate', title: 'Heart Rate', items: activityDetail.value.heartRate },
    { key: 'location-timing', title: 'Location & Timing', items: activityDetail.value.locationAndTiming }
  ].filter((section) => section.items.length > 0);
});

const detailDialogTitle = computed(() => selectedActivity.value?.activityName || 'Activity Details');

const formatNumber = (value: number | null | undefined, unit = '', maximumFractionDigits = 0) => {
  if (value == null || Number.isNaN(value)) {
    return 'Unavailable';
  }

  const formatted = new Intl.NumberFormat(undefined, {
    minimumFractionDigits: maximumFractionDigits > 0 ? 1 : 0,
    maximumFractionDigits
  }).format(value);

  return unit ? `${formatted} ${unit}` : formatted;
};

const formatDuration = (seconds: number | null | undefined) => {
  if (seconds == null || Number.isNaN(seconds)) {
    return 'Unavailable';
  }

  const roundedSeconds = Math.max(0, Math.round(seconds));
  const hours = Math.floor(roundedSeconds / 3600);
  const minutes = Math.floor((roundedSeconds % 3600) / 60);
  const remainingSeconds = roundedSeconds % 60;

  if (hours > 0) {
    return `${hours}h ${String(minutes).padStart(2, '0')}m ${String(remainingSeconds).padStart(2, '0')}s`;
  }

  if (minutes > 0) {
    return `${minutes}m ${String(remainingSeconds).padStart(2, '0')}s`;
  }

  return `${remainingSeconds}s`;
};

const humanizeIdentifier = (value: string | null | undefined) => {
  if (!value) {
    return '—';
  }

  const normalized = value.replaceAll('_', ' ').trim();
  if (!normalized) {
    return '—';
  }

  return normalized.charAt(0).toUpperCase() + normalized.slice(1);
};

const formatValueOnly = (value: number | null | undefined, maximumFractionDigits = 0) => {
  if (value == null || Number.isNaN(value)) {
    return 'Unavailable';
  }

  return new Intl.NumberFormat(undefined, {
    minimumFractionDigits: maximumFractionDigits > 0 ? 1 : 0,
    maximumFractionDigits
  }).format(value);
};

const formatSignedValue = (value: number | null | undefined, maximumFractionDigits = 0) => {
  if (value == null || Number.isNaN(value)) {
    return 'Unavailable';
  }

  return new Intl.NumberFormat(undefined, {
    signDisplay: 'exceptZero',
    minimumFractionDigits: maximumFractionDigits > 0 ? 1 : 0,
    maximumFractionDigits
  }).format(value);
};

const formatPercent = (value: number | null | undefined, maximumFractionDigits = 0) => {
  if (value == null || Number.isNaN(value)) {
    return 'Unavailable';
  }

  return `${new Intl.NumberFormat(undefined, {
    minimumFractionDigits: maximumFractionDigits > 0 ? 1 : 0,
    maximumFractionDigits
  }).format(value)}%`;
};

const formatBmiBandRange = (min: number, max: number) => {
  if (max === Number.POSITIVE_INFINITY) {
    return `${min.toFixed(1)}+`;
  }

  return `${min.toFixed(1)} - ${max.toFixed(1)}`;
};

const setHoveredSummaryTarget = (card: SummaryCardKey, target: string) => {
  if (!supportsHoverInteraction.value) {
    return;
  }

  hoveredSummaryTargets[card] = target;
};

const clearHoveredSummaryTarget = (card: SummaryCardKey) => {
  hoveredSummaryTargets[card] = null;
};

const isSummaryTargetActive = (card: SummaryCardKey, target: string) => {
  return hoveredSummaryTargets[card] === target;
};

const setHoveredCalorieTarget = (target: string) => {
  if (!supportsHoverInteraction.value) {
    return;
  }

  hoveredCalorieTarget.value = target;
};

const clearHoveredCalorieTarget = () => {
  hoveredCalorieTarget.value = null;
};

onMounted(() => {
  supportsHoverInteraction.value = window.matchMedia('(hover: hover) and (pointer: fine)').matches;
});

onBeforeUnmount(() => {
  if (activitySearchTimer) {
    clearTimeout(activitySearchTimer);
  }
});

const cloneDate = (value: Date | null) => {
  return value ? new Date(value.getTime()) : null;
};

const resetSummaryCards = () => {
  heartRateCard.selectedDate = null;
  heartRateCard.data = null;
  heartRateCard.loading = false;
  heartRateCard.error = '';

  weightCard.selectedDate = null;
  weightCard.data = null;
  weightCard.loading = false;
  weightCard.error = '';

  caloriesCard.selectedDate = null;
  caloriesCard.data = null;
  caloriesCard.loading = false;
  caloriesCard.error = '';

  stressCard.selectedDate = null;
  stressCard.data = null;
  stressCard.loading = false;
  stressCard.error = '';
};

const showCardLoadError = (message: string) => {
  showToast('error', message);
};

const loadSummary = async () => {
  const requestId = ++summaryRequestId;
  summaryLoading.value = true;
  summaryError.value = '';

  const result = await healthApi.getSummary();
  if (requestId !== summaryRequestId) {
    return;
  }

  if (!result.success) {
    summary.value = null;
    summaryError.value = result.message ?? 'Unable to load health summary.';
    resetSummaryCards();
    summaryLoading.value = false;
    return;
  }

  summary.value = result.data ?? null;

  if (!summary.value) {
    resetSummaryCards();
    summaryLoading.value = false;
    return;
  }

  await Promise.all([
    loadHeartRateCard(),
    loadWeightCard(),
    loadCaloriesCard(),
    loadStressCard()
  ]);

  if (requestId !== summaryRequestId) {
    return;
  }

  summaryLoading.value = false;
};

const loadHeartRateCard = async (date?: string | null) => {
  const requestId = ++heartRateCardRequestId;
  heartRateCard.loading = true;
  heartRateCard.error = '';

  const result = await healthApi.getHeartRateCard(date);
  if (requestId !== heartRateCardRequestId) {
    return;
  }

  heartRateCard.loading = false;

  if (!result.success || !result.data) {
    heartRateCard.data = null;
    heartRateCard.error = result.message ?? 'Unable to load heart rate data.';
    showCardLoadError(heartRateCard.error);
    return;
  }

  heartRateCard.selectedDate = parseIsoDate(result.data.date) ?? heartRateCard.selectedDate;
  heartRateCard.data = result.data.data ?? null;
};

const loadWeightCard = async (date?: string | null) => {
  const requestId = ++weightCardRequestId;
  weightCard.loading = true;
  weightCard.error = '';

  const result = await healthApi.getWeightCard(date);
  if (requestId !== weightCardRequestId) {
    return;
  }

  weightCard.loading = false;

  if (!result.success || !result.data) {
    weightCard.data = null;
    weightCard.error = result.message ?? 'Unable to load weight data.';
    showCardLoadError(weightCard.error);
    return;
  }

  weightCard.selectedDate = parseIsoDate(result.data.date) ?? weightCard.selectedDate;
  weightCard.data = result.data.data ?? null;
};

const loadCaloriesCard = async (date?: string | null) => {
  const requestId = ++caloriesCardRequestId;
  caloriesCard.loading = true;
  caloriesCard.error = '';

  const result = await healthApi.getCaloriesCard(date);
  if (requestId !== caloriesCardRequestId) {
    return;
  }

  caloriesCard.loading = false;

  if (!result.success || !result.data) {
    caloriesCard.data = null;
    caloriesCard.error = result.message ?? 'Unable to load calories data.';
    showCardLoadError(caloriesCard.error);
    return;
  }

  caloriesCard.selectedDate = parseIsoDate(result.data.date) ?? caloriesCard.selectedDate;
  caloriesCard.data = result.data.data ?? null;
};

const loadStressCard = async (date?: string | null) => {
  const requestId = ++stressCardRequestId;
  stressCard.loading = true;
  stressCard.error = '';

  const result = await healthApi.getStressCard(date);
  if (requestId !== stressCardRequestId) {
    return;
  }

  stressCard.loading = false;

  if (!result.success || !result.data) {
    stressCard.data = null;
    stressCard.error = result.message ?? 'Unable to load stress data.';
    showCardLoadError(stressCard.error);
    return;
  }

  stressCard.selectedDate = parseIsoDate(result.data.date) ?? stressCard.selectedDate;
  stressCard.data = result.data.data ?? null;
};

const handleSummaryCardDateChange = (card: SummaryDateCardKey, value: DatePickerValue) => {
  const candidate = Array.isArray(value) ? value.find((item): item is Date => item instanceof Date && !Number.isNaN(item.getTime())) ?? null : value;
  const selectedDate = candidate instanceof Date && !Number.isNaN(candidate.getTime()) ? candidate : null;
  if (!selectedDate) {
    return;
  }

  const formattedDate = formatIsoDate(selectedDate);
  if (!formattedDate) {
    return;
  }

  switch (card) {
    case 'heart-rate':
      heartRateCard.selectedDate = cloneDate(selectedDate);
      loadHeartRateCard(formattedDate);
      break;
    case 'weight':
      weightCard.selectedDate = cloneDate(selectedDate);
      loadWeightCard(formattedDate);
      break;
    case 'calories':
      caloriesCard.selectedDate = cloneDate(selectedDate);
      loadCaloriesCard(formattedDate);
      break;
    case 'stress':
      stressCard.selectedDate = cloneDate(selectedDate);
      loadStressCard(formattedDate);
      break;
    default:
      break;
  }
};

const loadActivities = async () => {
  const requestId = ++activityRequestId;
  activitiesLoading.value = true;
  activitiesError.value = '';

  const result = await healthApi.listActivities({
    page: activityPage.page,
    pageSize: activityPage.pageSize,
    search: activitySearchInput.value
  });
  if (requestId !== activityRequestId) {
    return;
  }

  activitiesLoading.value = false;

  if (!result.success || !result.data) {
    activities.value = [];
    activityPage.total = 0;
    activityPage.totalPages = 0;
    activitiesError.value = result.message ?? 'Unable to load activity history.';
    return;
  }

  activities.value = result.data.items;
  activityPage.total = result.data.page.total;
  activityPage.totalPages = result.data.page.totalPages;
};

const openActivityDetail = async (item: HealthActivityListItem) => {
  selectedActivity.value = item;
  detailDialogVisible.value = true;
  detailLoading.value = true;
  detailError.value = '';
  activityDetail.value = null;

  if (activityDetailCache[item.activityRecordId]) {
    activityDetail.value = activityDetailCache[item.activityRecordId];
    detailLoading.value = false;
    return;
  }

  const requestId = ++detailRequestId;
  const result = await healthApi.getActivityDetail(item.activityRecordId);
  if (requestId !== detailRequestId) {
    return;
  }

  detailLoading.value = false;

  if (!result.success || !result.data) {
    detailError.value = result.message ?? 'Unable to load activity details.';
    showToast('error', detailError.value);
    return;
  }

  activityDetailCache[item.activityRecordId] = result.data;
  activityDetail.value = result.data;
};

const onActivityPage = (event: DataTablePageEvent) => {
  activityPage.page = event.page + 1;
  activityPage.pageSize = event.rows;
  loadActivities();
};

const triggerActivitySearch = () => {
  if (activitySearchTimer) {
    clearTimeout(activitySearchTimer);
  }

  activityPage.page = 1;
  loadActivities();
};

watch(activitySearchInput, () => {
  if (!auth.isAuthenticated) {
    return;
  }

  if (activitySearchTimer) {
    clearTimeout(activitySearchTimer);
  }

  activitySearchTimer = setTimeout(() => {
    activityPage.page = 1;
    loadActivities();
  }, 250);
});

watch(
  () => auth.isAuthenticated,
  (isAuthenticated) => {
    if (!isAuthenticated) {
      summary.value = null;
      resetSummaryCards();
      activities.value = [];
      activityPage.total = 0;
      activityPage.totalPages = 0;
      return;
    }

    activityPage.page = 1;
    loadSummary();
    loadActivities();
  },
  { immediate: true }
);
</script>

<template>
  <div class="space-y-6">
    <div v-if="summaryError">
      <Message severity="error" :closable="false">{{ summaryError }}</Message>
    </div>

    <div v-if="summaryLoading" class="grid gap-4 xl:grid-cols-4 md:grid-cols-2">
      <div
        v-for="index in 4"
        :key="index"
        class="rounded-3xl border border-slate-200/80 bg-white/90 p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900/70"
      >
        <div class="flex items-center justify-between gap-4">
          <Skeleton width="36%" height="0.9rem" />
          <Skeleton width="4.5rem" height="1.85rem" border-radius="999px" />
        </div>
        <div class="mt-6">
          <Skeleton width="58%" height="2.7rem" />
          <Skeleton width="28%" height="0.9rem" class="mt-2" />
        </div>
        <div class="mt-6">
          <Skeleton width="100%" height="7.5rem" />
        </div>
      </div>
    </div>

    <div
      v-else-if="summary"
      class="grid gap-4 xl:grid-cols-4 md:grid-cols-2"
    >
      <article class="h-[23rem] overflow-hidden rounded-2xl border border-slate-200/80 bg-white/90 shadow-sm transition-all duration-200 ease-out hover:-translate-y-0.5 hover:border-slate-300/90 hover:shadow-md dark:border-slate-700 dark:bg-slate-900/70 dark:hover:border-slate-600">
        <div class="flex h-full flex-col overflow-hidden">
          <div class="border-b border-slate-200/80 px-5 py-2 dark:border-slate-800">
            <div class="flex min-h-[2.5rem] items-center justify-between gap-3">
              <p class="text-sm font-semibold uppercase tracking-[0.18em] text-slate-500 dark:text-slate-400">HEART RATE</p>
              <DatePicker
                :model-value="heartRateCard.selectedDate"
                class="w-[10rem]"
                input-class="w-full"
                placeholder="YYYY-MM-DD"
                date-format="yy-mm-dd"
                show-icon
                icon-display="input"
                size="small"
                :manual-input="false"
                :max-date="dashboardMaxDate"
                @update:model-value="handleSummaryCardDateChange('heart-rate', $event)"
              />
            </div>
          </div>

          <div v-if="heartRateCard.loading" class="flex min-h-0 flex-1 items-center justify-center p-5">
            <div class="flex h-full w-full items-center justify-center rounded-[1.65rem] border border-dashed border-slate-200 bg-slate-50/80 text-sm font-medium text-slate-500 dark:border-slate-700 dark:bg-slate-800/60 dark:text-slate-400">
              Loading...
            </div>
          </div>

          <div v-else-if="heartRateCard.error" class="flex min-h-0 flex-1 items-center justify-center p-5">
            <div class="flex h-full w-full items-center justify-center rounded-[1.65rem] border border-dashed border-rose-200 bg-rose-50/80 px-5 text-center text-sm font-medium text-rose-600 dark:border-rose-900/70 dark:bg-rose-950/30 dark:text-rose-300">
              {{ heartRateCard.error }}
            </div>
          </div>

          <div v-else-if="heartRateHasData" class="flex min-h-0 flex-1 flex-col justify-between overflow-hidden px-4 pb-4 pt-4">
            <div class="relative flex min-h-0 flex-1 items-center justify-center pb-1">
              <div
                v-if="hoveredHeartRateInfo"
                class="pointer-events-none absolute left-1/2 top-1 z-10 w-[10.5rem] -translate-x-1/2 rounded-2xl border border-slate-200/90 bg-white/95 px-3 py-2 text-center shadow-sm backdrop-blur-sm dark:border-slate-700 dark:bg-slate-900/95"
              >
                <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-400 dark:text-slate-500">
                  {{ hoveredHeartRateInfo.label }}
                </p>
                <p class="mt-1 text-sm font-semibold text-slate-900 dark:text-slate-100">
                  {{ hoveredHeartRateInfo.value }}
                </p>
                <p v-if="hoveredHeartRateInfo.meta" class="mt-1 text-[11px] text-slate-500 dark:text-slate-400">
                  {{ hoveredHeartRateInfo.meta }}
                </p>
              </div>

              <svg
                viewBox="0 0 220 220"
                class="h-[13.25rem] w-[13.25rem] overflow-visible md:h-[13.75rem] md:w-[13.75rem]"
                aria-label="Heart rate zones"
              >
                <circle
                  cx="110"
                  cy="110"
                  r="72"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="16"
                  class="text-slate-200/80 dark:text-slate-800"
                />
                <path
                  v-for="segment in heartRateRingSegments"
                  :key="segment.key"
                  :d="segment.path"
                  fill="none"
                  stroke="currentColor"
                  :stroke-width="isSummaryTargetActive('heart-rate', segment.key) ? 18 : 16"
                  stroke-linecap="butt"
                  :tabindex="supportsHoverInteraction ? 0 : -1"
                  :aria-label="`${segment.label} ${heartRateRangeLabel(segment)}`"
                  :class="[
                    segment.colorClass,
                    hoveredSummaryTargets['heart-rate'] && !isSummaryTargetActive('heart-rate', segment.key) ? 'opacity-30' : 'opacity-100'
                  ]"
                  @mouseenter="setHoveredSummaryTarget('heart-rate', segment.key)"
                  @mouseleave="clearHoveredSummaryTarget('heart-rate')"
                  @focus="setHoveredSummaryTarget('heart-rate', segment.key)"
                  @blur="clearHoveredSummaryTarget('heart-rate')"
                />
                <g v-if="averageHeartRateMarker">
                  <circle
                    :cx="averageHeartRateMarker.x"
                    :cy="averageHeartRateMarker.y"
                    r="7"
                    class="fill-white stroke-slate-900/80 dark:fill-slate-900 dark:stroke-slate-100/80"
                    stroke-width="2"
                  />
                  <circle
                    :cx="averageHeartRateMarker.x"
                    :cy="averageHeartRateMarker.y"
                    r="3.5"
                    class="fill-slate-900 dark:fill-slate-100"
                  />
                </g>
              </svg>

              <div class="pointer-events-none absolute inset-0 flex items-center justify-center">
                <div class="flex w-24 flex-col items-center justify-center text-center">
                  <p
                    class="font-semibold tracking-[-0.05em] text-slate-950 dark:text-slate-50"
                    :class="heartRateData?.average != null ? 'text-[2.15rem]' : 'text-base'"
                  >
                    {{ heartRateData?.average != null ? formatValueOnly(heartRateData.average, 0) : 'Unavailable' }}
                  </p>
                  <p class="mt-1 text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-400 dark:text-slate-500">AVG BPM</p>
                </div>
              </div>
            </div>

            <div class="grid shrink-0 grid-cols-2 gap-2.5">
              <div class="rounded-2xl border border-slate-200/80 bg-slate-50/80 px-3 py-2.5 dark:border-slate-700 dark:bg-slate-800/60">
                <p class="text-[11px] font-semibold uppercase tracking-[0.12em] text-slate-400 dark:text-slate-500">Resting</p>
                <p class="mt-1 text-sm font-semibold text-slate-900 dark:text-slate-100">{{ formatNumber(heartRateData?.resting ?? null, 'bpm', 0) }}</p>
              </div>
              <div class="rounded-2xl border border-slate-200/80 bg-slate-50/80 px-3 py-2.5 dark:border-slate-700 dark:bg-slate-800/60">
                <p class="text-[11px] font-semibold uppercase tracking-[0.12em] text-slate-400 dark:text-slate-500">Highest</p>
                <p class="mt-1 text-sm font-semibold text-slate-900 dark:text-slate-100">{{ formatNumber(heartRateData?.highest ?? null, 'bpm', 0) }}</p>
              </div>
            </div>
          </div>

          <div v-else class="flex min-h-0 flex-1 items-center justify-center p-5">
            <div class="flex h-full w-full items-center justify-center rounded-[1.65rem] border border-dashed border-slate-200 bg-slate-50/80 text-sm font-medium text-slate-500 dark:border-slate-700 dark:bg-slate-800/60 dark:text-slate-400">
              Unavailable
            </div>
          </div>
        </div>
      </article>

      <article class="h-[23rem] overflow-hidden rounded-2xl border border-slate-200/80 bg-white/90 shadow-sm transition-all duration-200 ease-out hover:-translate-y-0.5 hover:border-slate-300/90 hover:shadow-md dark:border-slate-700 dark:bg-slate-900/70 dark:hover:border-slate-600">
        <div class="flex h-full flex-col overflow-hidden">
          <div class="border-b border-slate-200/80 px-5 py-2 dark:border-slate-800">
            <div class="flex min-h-[2.5rem] items-center justify-between gap-3">
              <p class="text-sm font-semibold uppercase tracking-[0.18em] text-slate-500 dark:text-slate-400">WEIGHT</p>
              <DatePicker
                :model-value="weightCard.selectedDate"
                class="w-[10rem]"
                input-class="w-full"
                placeholder="YYYY-MM-DD"
                date-format="yy-mm-dd"
                show-icon
                icon-display="input"
                size="small"
                :manual-input="false"
                :max-date="dashboardMaxDate"
                @update:model-value="handleSummaryCardDateChange('weight', $event)"
              />
            </div>
          </div>

          <div v-if="weightCard.loading" class="flex min-h-0 flex-1 items-center justify-center p-5">
            <div class="flex h-full w-full items-center justify-center rounded-[1.65rem] border border-dashed border-slate-200 bg-slate-50/80 text-sm font-medium text-slate-500 dark:border-slate-700 dark:bg-slate-800/60 dark:text-slate-400">
              Loading...
            </div>
          </div>

          <div v-else-if="weightCard.error" class="flex min-h-0 flex-1 items-center justify-center p-5">
            <div class="flex h-full w-full items-center justify-center rounded-[1.65rem] border border-dashed border-rose-200 bg-rose-50/80 px-5 text-center text-sm font-medium text-rose-600 dark:border-rose-900/70 dark:bg-rose-950/30 dark:text-rose-300">
              {{ weightCard.error }}
            </div>
          </div>

          <div v-else-if="weightHasData" class="flex min-h-0 flex-1 flex-col justify-between overflow-hidden px-4 pb-4 pt-4">
            <div class="flex items-start justify-between gap-4">
              <div>
                <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-400 dark:text-slate-500">Current Weight</p>
                <p class="mt-2 text-[2.55rem] font-semibold tracking-[-0.05em] text-slate-950 dark:text-slate-50">
                  {{ formatValueOnly(weightData?.weightKg ?? null, 1) }}
                </p>
                <p class="mt-1 text-sm font-medium text-slate-500 dark:text-slate-400">kg</p>
              </div>

              <div class="max-w-[8.75rem] text-right">
                <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-400 dark:text-slate-500">Vs Previous</p>
                <p class="mt-2 text-lg font-semibold" :class="weightDeltaToneClass">
                  {{ hasWeightDelta ? `${formatSignedValue(weightData?.weightDeltaKg ?? null, 1)} kg` : 'No previous record' }}
                </p>
                <p class="mt-1 text-[11px] text-slate-500 dark:text-slate-400">
                  {{
                    hasWeightDelta
                      ? `${formatSignedValue(weightData?.weightDeltaPercent ?? null, 1)}% · Prev ${formatValueOnly(weightData?.previousWeightKg ?? null, 1)} kg`
                      : 'Previous weigh-in unavailable'
                  }}
                </p>
              </div>
            </div>

            <div class="relative mt-4 rounded-[1.75rem] border border-slate-200/80 bg-slate-50/70 px-4 py-4 dark:border-slate-700 dark:bg-slate-800/50">
              <div
                v-if="hoveredWeightInfo"
                class="pointer-events-none absolute left-1/2 top-3 z-10 w-[10.5rem] -translate-x-1/2 rounded-2xl border border-slate-200/90 bg-white/95 px-3 py-2 text-center shadow-sm backdrop-blur-sm dark:border-slate-700 dark:bg-slate-900/95"
              >
                <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-400 dark:text-slate-500">
                  {{ hoveredWeightInfo.label }}
                </p>
                <p class="mt-1 text-sm font-semibold text-slate-900 dark:text-slate-100">
                  {{ hoveredWeightInfo.value }}
                </p>
              </div>

              <div class="flex items-end justify-between gap-3">
                <div>
                  <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-400 dark:text-slate-500">BMI</p>
                  <p class="mt-2 text-[2rem] font-semibold tracking-[-0.05em] text-slate-950 dark:text-slate-50">
                    {{ formatValueOnly(weightData?.bmi ?? null, 1) }}
                  </p>
                </div>

                <div class="text-right">
                  <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-400 dark:text-slate-500">
                    {{ bmiCategory?.label ?? 'Unavailable' }}
                  </p>
                  <p class="mt-2 text-sm font-semibold text-slate-900 dark:text-slate-100">
                    {{ bmiCategory ? formatBmiBandRange(bmiCategory.min, bmiCategory.max) : 'No BMI recorded' }}
                  </p>
                </div>
              </div>

              <div class="relative mt-5 px-1 pt-2">
                <div class="relative h-5 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
                  <button
                    v-for="band in bmiBands"
                    :key="band.key"
                    type="button"
                    class="absolute inset-y-0 border-r border-white/80 outline-none transition-all duration-200 focus-visible:ring-2 focus-visible:ring-slate-300 dark:border-slate-950 dark:focus-visible:ring-slate-600"
                    :class="[
                      band.barClass,
                      hoveredSummaryTargets.weight
                        ? (isSummaryTargetActive('weight', band.key) ? 'opacity-100 shadow-inner' : 'opacity-45')
                        : 'opacity-100'
                    ]"
                    :style="{
                      left: `${band.leftPercent}%`,
                      width: `${band.widthPercent}%`
                    }"
                    :aria-label="`${band.label} BMI ${formatBmiBandRange(band.min, band.max)}`"
                    @mouseenter="setHoveredSummaryTarget('weight', band.key)"
                    @mouseleave="clearHoveredSummaryTarget('weight')"
                    @focus="setHoveredSummaryTarget('weight', band.key)"
                    @blur="clearHoveredSummaryTarget('weight')"
                  />
                  <button
                    v-if="bmiMarkerPosition"
                    type="button"
                    class="absolute top-1/2 z-10 h-5 w-5 -translate-y-1/2 -translate-x-1/2 rounded-full border-4 border-white bg-slate-900 shadow-sm outline-none transition-transform duration-200 focus-visible:ring-2 focus-visible:ring-slate-300 dark:border-slate-900 dark:bg-slate-100 dark:focus-visible:ring-slate-600"
                    :class="isSummaryTargetActive('weight', 'marker') ? 'scale-110' : ''"
                    :style="{ left: bmiMarkerPosition }"
                    aria-label="Current BMI marker"
                    @mouseenter="setHoveredSummaryTarget('weight', 'marker')"
                    @mouseleave="clearHoveredSummaryTarget('weight')"
                    @focus="setHoveredSummaryTarget('weight', 'marker')"
                    @blur="clearHoveredSummaryTarget('weight')"
                  />
                </div>

                <div class="mt-3 flex items-center justify-between gap-2 text-[10px] font-semibold uppercase tracking-[0.12em] text-slate-400 dark:text-slate-500">
                  <span v-for="band in bmiBands" :key="`${band.key}-label`">{{ band.label }}</span>
                </div>
              </div>
            </div>
          </div>

          <div v-else class="flex min-h-0 flex-1 items-center justify-center p-5">
            <div class="flex h-full w-full items-center justify-center rounded-[1.65rem] border border-dashed border-slate-200 bg-slate-50/80 text-sm font-medium text-slate-500 dark:border-slate-700 dark:bg-slate-800/60 dark:text-slate-400">
              Unavailable
            </div>
          </div>
        </div>
      </article>

      <article class="h-[23rem] overflow-hidden rounded-2xl border border-slate-200/80 bg-white/90 shadow-sm transition-all duration-200 ease-out hover:-translate-y-0.5 hover:border-slate-300/90 hover:shadow-md dark:border-slate-700 dark:bg-slate-900/70 dark:hover:border-slate-600">
        <div class="flex h-full flex-col overflow-hidden">
          <div class="border-b border-slate-200/80 px-5 py-2 dark:border-slate-800">
            <div class="flex min-h-[2.5rem] items-center justify-between gap-3">
              <p class="text-sm font-semibold uppercase tracking-[0.18em] text-slate-500 dark:text-slate-400">CALORIES BURNED</p>
              <DatePicker
                :model-value="caloriesCard.selectedDate"
                class="w-[10rem]"
                input-class="w-full"
                placeholder="YYYY-MM-DD"
                date-format="yy-mm-dd"
                show-icon
                icon-display="input"
                size="small"
                :manual-input="false"
                :max-date="dashboardMaxDate"
                @update:model-value="handleSummaryCardDateChange('calories', $event)"
              />
            </div>
          </div>

          <div v-if="caloriesCard.loading" class="flex min-h-0 flex-1 items-center justify-center p-5">
            <div class="flex h-full w-full items-center justify-center rounded-[1.65rem] border border-dashed border-slate-200 bg-slate-50/80 text-sm font-medium text-slate-500 dark:border-slate-700 dark:bg-slate-800/60 dark:text-slate-400">
              Loading...
            </div>
          </div>

          <div v-else-if="caloriesCard.error" class="flex min-h-0 flex-1 items-center justify-center p-5">
            <div class="flex h-full w-full items-center justify-center rounded-[1.65rem] border border-dashed border-rose-200 bg-rose-50/80 px-5 text-center text-sm font-medium text-rose-600 dark:border-rose-900/70 dark:bg-rose-950/30 dark:text-rose-300">
              {{ caloriesCard.error }}
            </div>
          </div>

          <div v-else-if="totalCaloriesBurned != null" class="grid min-h-0 flex-1 grid-rows-[auto,1fr,auto] gap-4 overflow-hidden px-4 pb-4 pt-4">
            <div class="flex shrink-0 flex-col items-center justify-center text-center">
              <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-400 dark:text-slate-500">Total Burn</p>
              <p class="mt-2 text-[2.55rem] font-semibold tracking-[-0.05em] text-slate-950 dark:text-slate-50">
                {{ formatValueOnly(totalCaloriesBurned, 0) }}
              </p>
              <p class="mt-1 text-sm font-medium text-slate-500 dark:text-slate-400">kcal</p>
            </div>

            <div class="relative flex min-h-0 items-center justify-center px-1 pt-8">
              <div
                v-if="hoveredCalorieInfo"
                class="pointer-events-none absolute left-1/2 top-0 z-10 -translate-x-1/2 rounded-2xl border border-slate-200/90 bg-white/95 px-3 py-2 text-center shadow-sm backdrop-blur-sm dark:border-slate-700 dark:bg-slate-900/95"
              >
                <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-400 dark:text-slate-500">
                  {{ hoveredCalorieInfo.label }}
                </p>
                <p class="mt-1 text-sm font-semibold text-slate-900 dark:text-slate-100">
                  {{ hoveredCalorieInfo.value }}
                </p>
              </div>

              <div class="w-full px-1">
                <div class="flex h-6 overflow-hidden rounded-full ring-1 ring-slate-200/80 dark:ring-slate-700">
                  <button
                    v-for="segment in calorieSegments"
                    :key="segment.key"
                    type="button"
                    class="h-full outline-none transition-opacity duration-200 hover:opacity-95"
                    :class="segment.barClass"
                    :style="{ width: segment.width }"
                    :aria-label="`${segment.label} ${formatPercent(segment.share, 0)}`"
                    @mouseenter="setHoveredCalorieTarget(segment.key)"
                    @mouseleave="clearHoveredCalorieTarget"
                    @focus="setHoveredCalorieTarget(segment.key)"
                    @blur="clearHoveredCalorieTarget"
                  />
                </div>
              </div>
            </div>

            <div class="grid shrink-0 grid-cols-2 gap-2.5">
              <div
                v-for="segment in calorieBreakdownItems"
                :key="`${segment.key}-tile`"
                class="flex min-h-[5.25rem] flex-col justify-between rounded-2xl border border-slate-200/80 bg-slate-50/80 px-3 py-3.5 dark:border-slate-700 dark:bg-slate-800/60"
              >
                <div class="flex items-center gap-2">
                  <span class="h-2.5 w-2.5 rounded-full" :class="segment.barClass" />
                  <span class="text-xs font-semibold uppercase tracking-[0.12em] text-slate-400 dark:text-slate-500">{{ segment.label }}</span>
                </div>
                <p class="mt-2 text-lg font-semibold text-slate-900 dark:text-slate-100">{{ formatNumber(segment.value, 'kcal', 0) }}</p>
              </div>
            </div>
          </div>

          <div v-else class="flex min-h-0 flex-1 items-center justify-center p-5">
            <div class="flex h-full w-full items-center justify-center rounded-[1.65rem] border border-dashed border-slate-200 bg-slate-50/80 text-sm font-medium text-slate-500 dark:border-slate-700 dark:bg-slate-800/60 dark:text-slate-400">
              Unavailable
            </div>
          </div>
        </div>
      </article>

      <article class="h-[23rem] overflow-hidden rounded-2xl border border-slate-200/80 bg-white/90 shadow-sm transition-all duration-200 ease-out hover:-translate-y-0.5 hover:border-slate-300/90 hover:shadow-md dark:border-slate-700 dark:bg-slate-900/70 dark:hover:border-slate-600">
        <div class="flex h-full flex-col overflow-hidden">
          <div class="border-b border-slate-200/80 px-5 py-2 dark:border-slate-800">
            <div class="flex min-h-[2.5rem] items-center justify-between gap-3">
              <p class="text-sm font-semibold uppercase tracking-[0.18em] text-slate-500 dark:text-slate-400">STRESS</p>
              <DatePicker
                :model-value="stressCard.selectedDate"
                class="w-[10rem]"
                input-class="w-full"
                placeholder="YYYY-MM-DD"
                date-format="yy-mm-dd"
                show-icon
                icon-display="input"
                size="small"
                :manual-input="false"
                :max-date="dashboardMaxDate"
                @update:model-value="handleSummaryCardDateChange('stress', $event)"
              />
            </div>
          </div>

          <div v-if="stressCard.loading" class="flex min-h-0 flex-1 items-center justify-center p-5">
            <div class="flex h-full w-full items-center justify-center rounded-[1.65rem] border border-dashed border-slate-200 bg-slate-50/80 text-sm font-medium text-slate-500 dark:border-slate-700 dark:bg-slate-800/60 dark:text-slate-400">
              Loading...
            </div>
          </div>

          <div v-else-if="stressCard.error" class="flex min-h-0 flex-1 items-center justify-center p-5">
            <div class="flex h-full w-full items-center justify-center rounded-[1.65rem] border border-dashed border-rose-200 bg-rose-50/80 px-5 text-center text-sm font-medium text-rose-600 dark:border-rose-900/70 dark:bg-rose-950/30 dark:text-rose-300">
              {{ stressCard.error }}
            </div>
          </div>

          <div v-else-if="stressHasData" class="flex min-h-0 flex-1 flex-col overflow-hidden px-4 pb-4 pt-4">
            <div class="flex h-full min-h-0 flex-col items-center justify-between">
              <div class="flex h-12 shrink-0 items-start justify-center">
                <div
                  v-if="hoveredStressInfo"
                  class="pointer-events-none rounded-2xl border border-slate-200/90 bg-white/95 px-3 py-2 text-center shadow-sm backdrop-blur-sm dark:border-slate-700 dark:bg-slate-900/95"
                >
                  <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-400 dark:text-slate-500">
                    {{ hoveredStressInfo.label }}
                  </p>
                  <p class="mt-1 text-sm font-semibold text-slate-900 dark:text-slate-100">
                    {{ hoveredStressInfo.value }}
                  </p>
                </div>
              </div>

              <div class="relative flex min-h-0 flex-1 items-center justify-center">
                <svg
                  viewBox="0 0 220 220"
                  class="h-[14.35rem] w-[14.35rem] overflow-visible md:h-[14.85rem] md:w-[14.85rem]"
                  aria-label="Stress distribution"
                >
                  <circle
                    cx="110"
                    cy="110"
                    :r="STRESS_RING_RADIUS"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="18"
                    class="text-slate-200/80 dark:text-slate-800"
                  />
                  <path
                    v-for="segment in stressRingSegments"
                    :key="segment.key"
                    :d="segment.path"
                    fill="none"
                    stroke="currentColor"
                    :stroke-width="isSummaryTargetActive('stress', segment.key) ? 20 : 18"
                    stroke-linecap="butt"
                    :tabindex="supportsHoverInteraction ? 0 : -1"
                    :aria-label="`${segment.label} ${formatDuration(segment.value)}`"
                    :class="[
                      segment.colorClass,
                      hoveredSummaryTargets.stress && !isSummaryTargetActive('stress', segment.key) ? 'opacity-30' : 'opacity-100'
                    ]"
                    @mouseenter="setHoveredSummaryTarget('stress', segment.key)"
                    @mouseleave="clearHoveredSummaryTarget('stress')"
                    @focus="setHoveredSummaryTarget('stress', segment.key)"
                    @blur="clearHoveredSummaryTarget('stress')"
                  />
                </svg>

                <div class="pointer-events-none absolute inset-0 flex items-center justify-center">
                  <div class="flex w-24 flex-col items-center justify-center text-center">
                    <p
                      class="font-semibold tracking-[-0.05em] text-slate-950 dark:text-slate-50"
                      :class="stressData?.overall != null ? 'text-[2.15rem]' : 'text-base'"
                    >
                      {{ stressData?.overall != null ? formatValueOnly(stressData.overall, 0) : 'Unavailable' }}
                    </p>
                    <p class="mt-1 text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-400 dark:text-slate-500">OVERALL</p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div v-else class="flex min-h-0 flex-1 items-center justify-center p-5">
            <div class="flex h-full w-full items-center justify-center rounded-[1.65rem] border border-dashed border-slate-200 bg-slate-50/80 text-sm font-medium text-slate-500 dark:border-slate-700 dark:bg-slate-800/60 dark:text-slate-400">
              Unavailable
            </div>
          </div>
        </div>
      </article>
    </div>

    <div
      v-else
      class="app-panel rounded-3xl p-8 text-center"
    >
      <p class="text-sm font-medium text-slate-500 dark:text-slate-400">No health summary available from marts yet.</p>
    </div>

    <article class="h-[42rem] overflow-hidden rounded-2xl border border-slate-200/80 bg-white/90 shadow-sm transition-all duration-200 ease-out hover:border-slate-300/90 hover:shadow-md dark:border-slate-700 dark:bg-slate-900/70 dark:hover:border-slate-600">
      <div class="flex h-full flex-col overflow-hidden">
        <div class="border-b border-slate-200/80 px-5 py-2 dark:border-slate-800">
          <div class="flex flex-col gap-1 sm:min-h-[2.5rem] sm:flex-row sm:items-center sm:justify-between">
          <p class="text-sm font-semibold uppercase tracking-[0.18em] text-slate-500 dark:text-slate-400">ACTIVITY</p>
            <IconField class="w-full sm:w-72">
              <InputIcon class="pi pi-search" />
              <InputText
                v-model="activitySearchInput"
                type="search"
                placeholder="Search activities"
                class="h-9 w-full text-sm"
                @keydown.enter.prevent="triggerActivitySearch"
              />
            </IconField>
          </div>
        </div>

        <div class="flex min-h-0 flex-1 flex-col gap-4 px-4 pb-4 pt-4 sm:px-5">
          <Message v-if="activitiesError" severity="error" :closable="false">
            {{ activitiesError }}
          </Message>

          <div class="min-h-0 flex-1 overflow-hidden">
            <DataTable
              :value="activities"
              :loading="activitiesLoading"
              :rows="activityPage.pageSize"
              :first="(activityPage.page - 1) * activityPage.pageSize"
              :total-records="activityPage.total"
              class="activity-history-table h-full"
              lazy
              paginator
              scrollable
              scroll-height="flex"
              striped-rows
              table-style="min-width: 100%"
              paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport"
              current-page-report-template="{first} - {last} of {totalRecords}"
              @page="onActivityPage"
            >
              <Column field="activityName" header="Activity Name">
                <template #body="{ data }">
                  <span class="text-sm font-medium text-slate-800 dark:text-slate-100">{{ data.activityName || '—' }}</span>
                </template>
              </Column>

              <Column field="startTime" header="Start Time">
                <template #body="{ data }">
                  <span class="text-sm text-slate-600 dark:text-slate-300">{{ data.startTime || '—' }}</span>
                </template>
              </Column>

              <Column field="endTime" header="End Time">
                <template #body="{ data }">
                  <span class="text-sm text-slate-600 dark:text-slate-300">{{ data.endTime || '—' }}</span>
                </template>
              </Column>

              <Column field="durationSeconds" header="Duration">
                <template #body="{ data }">
                  <span class="text-sm text-slate-600 dark:text-slate-300">{{ formatDuration(data.durationSeconds) }}</span>
                </template>
              </Column>

              <Column field="calories" header="Calories">
                <template #body="{ data }">
                  <span class="text-sm text-slate-600 dark:text-slate-300">{{ formatNumber(data.calories, 'kcal', 0) }}</span>
                </template>
              </Column>

              <Column field="avgHeartRate" header="Avg HR">
                <template #body="{ data }">
                  <span
                    class="inline-flex items-center rounded-full border px-2 py-0.5 text-[0.7rem] font-semibold"
                    :class="activityAvgHeartRateClass(data.avgHeartRate)"
                  >
                    {{ formatNumber(data.avgHeartRate, 'bpm', 0) }}
                  </span>
                </template>
              </Column>

              <Column field="maxHeartRate" header="Max HR">
                <template #body="{ data }">
                  <span class="text-sm text-slate-600 dark:text-slate-300">{{ formatNumber(data.maxHeartRate, 'bpm', 0) }}</span>
                </template>
              </Column>

              <Column header="Actions" style="width: 10rem">
                <template #body="{ data }">
                  <Button
                    label="View Details"
                    text
                    severity="secondary"
                    class="whitespace-nowrap !px-2 !py-1 text-xs"
                    @click="openActivityDetail(data)"
                  />
                </template>
              </Column>

              <template #empty>
                <div class="flex min-h-[18rem] items-center justify-center text-center text-sm text-slate-500 dark:text-slate-400">
                  No activity history available.
                </div>
              </template>
            </DataTable>
          </div>
        </div>
      </div>
    </article>

    <Dialog
      v-model:visible="detailDialogVisible"
      modal
      dismissable-mask
      :header="detailDialogTitle"
      class="w-[min(94vw,64rem)]"
    >
      <div class="space-y-5">
        <div
          v-if="selectedActivity"
          class="flex flex-col gap-2 rounded-2xl border border-slate-200/80 bg-slate-50/80 p-4 dark:border-slate-700 dark:bg-slate-900/70 sm:flex-row sm:items-center sm:justify-between"
        >
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400 dark:text-slate-500">Activity Type</p>
            <p class="mt-2 text-sm font-medium text-slate-700 dark:text-slate-200">{{ humanizeIdentifier(selectedActivity.activityType) }}</p>
          </div>
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400 dark:text-slate-500">Start Time</p>
            <p class="mt-2 text-sm font-medium text-slate-700 dark:text-slate-200">{{ selectedActivity.startTime || '—' }}</p>
          </div>
        </div>

        <Message v-if="detailError" severity="error" :closable="false">
          {{ detailError }}
        </Message>

        <div v-if="detailLoading" class="grid gap-4 lg:grid-cols-2">
          <div
            v-for="index in 4"
            :key="index"
            class="rounded-2xl border border-slate-200/80 bg-white/90 p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900/70"
          >
            <Skeleton width="40%" height="1rem" />
            <div class="mt-4 space-y-3">
              <Skeleton width="100%" height="1.1rem" />
              <Skeleton width="92%" height="1.1rem" />
              <Skeleton width="88%" height="1.1rem" />
            </div>
          </div>
        </div>

        <div v-else-if="activityDetail" class="grid gap-4 lg:grid-cols-2">
          <section
            v-for="section in detailSections"
            :key="section.key"
            class="rounded-2xl border border-slate-200/80 bg-white/90 p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900/70"
          >
            <h3 class="text-sm font-semibold uppercase tracking-[0.16em] text-slate-500 dark:text-slate-400">{{ section.title }}</h3>
            <div class="mt-4 space-y-3">
              <div
                v-for="item in section.items"
                :key="`${section.key}-${item.label}`"
                class="flex items-start justify-between gap-4 border-b border-slate-200/70 pb-3 last:border-b-0 last:pb-0 dark:border-slate-700/70"
              >
                <span class="text-sm text-slate-500 dark:text-slate-400">{{ item.label }}</span>
                <span class="max-w-[55%] text-right text-sm font-medium text-slate-800 dark:text-slate-100">{{ item.value }}</span>
              </div>
            </div>
          </section>
        </div>
      </div>
    </Dialog>
  </div>
</template>

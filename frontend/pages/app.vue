<script setup lang="ts">
import garminConnectLogo from '~/assets/images/garmin-connect-tile-120.png';
import { connectorApi } from '~/services/api/connectors';
import { getConnectorDefinition } from '~/services/connectors/catalog';
import { formatDateTime, getShanghaiStartOfDay, getUpcomingRunsFromCron, parseDateTime } from '~/services/connectors/cron';
import type {
  ConnectorCategory,
  ConnectorConfigValues,
  ConnectorRecord,
  ConnectorStatus,
  ListSyncJobsPayload,
  SyncJobDomain,
  SyncJobListFacets,
  SyncJobListItem,
  SyncJobPeriod,
  SyncJobStatus,
  SyncJobTriggerType
} from '~/types/connectors';
import { useAuthStore } from '~/stores/auth';
import type { AuthUser } from '~/types/auth';

definePageMeta({
  layout: 'authenticated',
  middleware: 'auth'
});

type TabChangeEvent = {
  index: number;
};

type SyncWindow = {
  startAt: Date | null;
  endAt: Date | null;
};

type SyncWindowField = keyof SyncWindow;

type DatePickerBlurEvent = {
  value?: string | null;
};

type SettingsProfileForm = {
  displayName: string;
  email: string;
  phone: string;
  avatarUrl: string;
};

type PasswordForm = {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
};

type ConnectorTaskStatus = SyncJobStatus;
type ConnectorTaskTriggerType = SyncJobTriggerType;
type ConnectorTaskDomain = SyncJobDomain;

const createEmptySyncJobFacets = (): SyncJobListFacets => ({
  allTasks: 0,
  status: {
    queued: 0,
    running: 0,
    success: 0,
    failed: 0
  },
  triggerType: {
    manual: 0,
    scheduled: 0
  },
  domain: {
    health: 0,
    finance: 0
  },
  period: {
    yesterday: 0,
    last7Days: 0,
    last30Days: 0
  }
});

const auth = useAuthStore();
const { showToast } = useAppToast();
const route = useRoute();
const activeTab = computed(() => {
  const tab = String(route.query.tab ?? 'health');
  return tab === 'connector' ? 'connector-settings' : tab;
});
const tabLabelMap: Record<string, string> = {
  health: 'Health',
  finance: 'Finance',
  'connector-settings': 'Connector Settings',
  'connector-tasks': 'Connector Tasks',
  staging: 'Staging',
  intermediate: 'Intermediate',
  marts: 'Marts',
  logs: 'Logs',
  settings: 'Settings'
};
const currentLabel = computed(() => tabLabelMap[activeTab.value] ?? 'Module');
const connectorActiveIndex = ref(0);
const connectorTabItems = [
  { label: 'Health' },
  { label: 'Finance' }
];
const connectorRecords = ref<ConnectorRecord[]>([]);
const connectorLoading = ref(false);
const connectorLoadError = ref('');
const connectorDialogVisible = ref(false);
const selectedConnectorId = ref<ConnectorRecord['id'] | null>(null);
const connectorDraftSchedule = ref('');
const connectorDraftConfig = reactive<ConnectorConfigValues>({});
const connectorTesting = ref(false);
const connectorSaving = ref(false);
const syncDialogVisible = ref(false);
const syncSubmitting = ref(false);
const selectedSyncConnectorId = ref<ConnectorRecord['id'] | null>(null);
const syncWindow = reactive<SyncWindow>({
  startAt: null,
  endAt: null
});
const syncInputValues = reactive<Record<SyncWindowField, string>>({
  startAt: '',
  endAt: ''
});
const syncFieldErrors = reactive<Record<SyncWindowField, string>>({
  startAt: '',
  endAt: ''
});
const profileForm = reactive<SettingsProfileForm>({
  displayName: '',
  email: '',
  phone: '',
  avatarUrl: ''
});
const passwordForm = reactive<PasswordForm>({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
});
const connectorTaskFilters = reactive<{
  status: 'all' | ConnectorTaskStatus;
  triggerType: 'all' | ConnectorTaskTriggerType;
  domain: 'all' | ConnectorTaskDomain;
}>({
  status: 'all',
  triggerType: 'all',
  domain: 'all'
});
const connectorTaskSearch = ref('');
const connectorTaskSearchQuery = ref('');
const connectorTaskPeriod = ref<SyncJobPeriod | null>(null);
const avatarInputRef = ref<HTMLInputElement | null>(null);
const profileSaving = ref(false);
const passwordSaving = ref(false);
const connectorStatusUpdatingIds = ref<ConnectorRecord['id'][]>([]);
const connectorDialogBusy = computed(() => connectorTesting.value || connectorSaving.value);
const connectorTaskRecords = ref<SyncJobListItem[]>([]);
const connectorTaskFacets = ref<SyncJobListFacets>(createEmptySyncJobFacets());
const connectorTaskLoading = ref(false);
const connectorTaskLoadingMore = ref(false);
const connectorTaskLoadError = ref('');
const connectorTaskPage = reactive({
  page: 1,
  pageSize: 20,
  total: 0,
  totalPages: 0
});
let connectorTaskSearchDebounceTimer: ReturnType<typeof setTimeout> | null = null;
let connectorTaskRequestSequence = 0;
const syncFieldLabelMap: Record<SyncWindowField, string> = {
  startAt: 'Start time',
  endAt: 'End time'
};

const buildConnectorTaskQuery = (page: number): ListSyncJobsPayload => {
  const payload: ListSyncJobsPayload = {
    page,
    pageSize: connectorTaskPage.pageSize,
    sortBy: 'createdAt',
    sortOrder: 'desc'
  };

  const normalizedSearch = connectorTaskSearchQuery.value.trim();
  if (normalizedSearch) {
    payload.search = normalizedSearch;
  }

  if (connectorTaskPeriod.value) {
    payload.period = connectorTaskPeriod.value;
  }

  if (connectorTaskFilters.status !== 'all') {
    payload.status = connectorTaskFilters.status;
  }

  if (connectorTaskFilters.triggerType !== 'all') {
    payload.triggerType = connectorTaskFilters.triggerType;
  }

  if (connectorTaskFilters.domain !== 'all') {
    payload.domain = connectorTaskFilters.domain;
  }

  return payload;
};

const loadConnectorTasks = async (options: { append: boolean } = { append: false }) => {
  const append = options.append;
  if (append) {
    if (connectorTaskLoading.value || connectorTaskLoadingMore.value) {
      return;
    }

    if (connectorTaskPage.totalPages > 0 && connectorTaskPage.page >= connectorTaskPage.totalPages) {
      return;
    }

    connectorTaskLoadingMore.value = true;
  } else {
    connectorTaskLoading.value = true;
    connectorTaskLoadError.value = '';
  }

  const targetPage = append ? connectorTaskPage.page + 1 : 1;
  const currentRequestId = ++connectorTaskRequestSequence;
  const result = await connectorApi.listSyncJobs(auth.token, buildConnectorTaskQuery(targetPage));

  if (currentRequestId !== connectorTaskRequestSequence) {
    return;
  }

  if (append) {
    connectorTaskLoadingMore.value = false;
  } else {
    connectorTaskLoading.value = false;
  }

  if (!result.success || !result.data) {
    const message = result.message ?? 'Unable to load sync tasks.';
    if (append) {
      showToast('error', message);
      return;
    }

    connectorTaskRecords.value = [];
    connectorTaskFacets.value = createEmptySyncJobFacets();
    connectorTaskPage.page = 1;
    connectorTaskPage.total = 0;
    connectorTaskPage.totalPages = 0;
    connectorTaskLoadError.value = message;
    return;
  }

  connectorTaskLoadError.value = '';
  connectorTaskFacets.value = result.data.facets ?? createEmptySyncJobFacets();
  connectorTaskPage.page = result.data.page.page;
  connectorTaskPage.pageSize = result.data.page.pageSize;
  connectorTaskPage.total = result.data.page.total;
  connectorTaskPage.totalPages = result.data.page.totalPages;
  connectorTaskRecords.value = append
    ? [...connectorTaskRecords.value, ...result.data.items]
    : result.data.items;
};

const reloadConnectorTasks = async () => {
  await loadConnectorTasks({ append: false });
};

const loadMoreConnectorTasks = async () => {
  await loadConnectorTasks({ append: true });
};

const connectorTaskStatusItems = computed(() => {
  return [
    { key: 'queued' as const, label: 'Pending', icon: 'pi pi-inbox', count: connectorTaskFacets.value.status.queued },
    { key: 'running' as const, label: 'In Progress', icon: 'pi pi-clock', count: connectorTaskFacets.value.status.running },
    { key: 'success' as const, label: 'Completed', icon: 'pi pi-check-circle', count: connectorTaskFacets.value.status.success },
    { key: 'failed' as const, label: 'Failed', icon: 'pi pi-times-circle', count: connectorTaskFacets.value.status.failed }
  ];
});

const connectorTaskTriggerTypeItems = computed(() => {
  return [
    { key: 'manual' as const, label: 'Manual', icon: 'pi pi-user-edit', count: connectorTaskFacets.value.triggerType.manual },
    { key: 'scheduled' as const, label: 'Scheduled', icon: 'pi pi-calendar-clock', count: connectorTaskFacets.value.triggerType.scheduled }
  ];
});

const connectorTaskDomainItems = computed(() => {
  return [
    { key: 'health' as const, label: 'Health', icon: 'pi pi-heart', count: connectorTaskFacets.value.domain.health },
    { key: 'finance' as const, label: 'Finance', icon: 'pi pi-chart-line', count: connectorTaskFacets.value.domain.finance }
  ];
});

const connectorTaskPeriodItems = computed(() => {
  return [
    { key: 'yesterday' as const, label: 'Yesterday', icon: 'pi pi-calendar-minus', count: connectorTaskFacets.value.period.yesterday },
    { key: 'last_7_days' as const, label: 'Last 7 days', icon: 'pi pi-calendar', count: connectorTaskFacets.value.period.last7Days },
    { key: 'last_30_days' as const, label: 'Last 30 days', icon: 'pi pi-calendar-plus', count: connectorTaskFacets.value.period.last30Days }
  ];
});

const isAllConnectorTasksSelected = computed(() => {
  return connectorTaskFilters.status === 'all'
    && connectorTaskFilters.triggerType === 'all'
    && connectorTaskFilters.domain === 'all'
    && !connectorTaskPeriod.value;
});
const hasMoreConnectorTasks = computed(() => {
  return connectorTaskPage.totalPages > 0 && connectorTaskPage.page < connectorTaskPage.totalPages;
});

const activeConnectorTaskFilterLabel = computed(() => {
  if (isAllConnectorTasksSelected.value) {
    return 'All Tasks';
  }

  const labels: string[] = [];

  if (connectorTaskFilters.status !== 'all') {
    labels.push(getConnectorTaskStatusLabel(connectorTaskFilters.status));
  }

  if (connectorTaskFilters.triggerType !== 'all') {
    labels.push(formatTriggerType(connectorTaskFilters.triggerType));
  }

  if (connectorTaskFilters.domain !== 'all') {
    labels.push(connectorTaskFilters.domain === 'health' ? 'Health' : 'Finance');
  }

  if (connectorTaskPeriod.value) {
    labels.push(getConnectorTaskPeriodLabel(connectorTaskPeriod.value));
  }

  return labels.join(' · ') || 'Filtered Tasks';
});

const activeConnectorCategory = computed<ConnectorCategory>(() => {
  return connectorActiveIndex.value === 1 ? 'finance' : 'health';
});
const connectorRows = computed(() => {
  return connectorRecords.value.filter((connector) => connector.category === activeConnectorCategory.value);
});
const selectedConnector = computed(() => {
  return connectorRecords.value.find((connector) => connector.id === selectedConnectorId.value) ?? null;
});
const selectedConnectorDefinition = computed(() => {
  return selectedConnector.value ? getConnectorDefinition(selectedConnector.value.id) ?? null : null;
});
const selectedSyncConnector = computed(() => {
  return connectorRecords.value.find((connector) => connector.id === selectedSyncConnectorId.value) ?? null;
});
const schedulePreviewResult = computed(() => {
  const schedule = connectorDraftSchedule.value.trim();
  if (!schedule) {
    return {
      success: false,
      message: 'Update frequency is required.'
    };
  }

  return getUpcomingRunsFromCron(schedule, 3);
});
const upcomingRunPreviews = computed(() => {
  if (!schedulePreviewResult.value.success || !schedulePreviewResult.value.runs?.length) {
    return [];
  }

  return schedulePreviewResult.value.runs.map((run) => formatDateTime(run));
});
const scheduleError = computed(() => {
  return schedulePreviewResult.value.success ? '' : schedulePreviewResult.value.message ?? 'Invalid cron expression.';
});
const syncWindowError = computed(() => {
  if (syncFieldErrors.startAt) {
    return syncFieldErrors.startAt;
  }

  if (syncFieldErrors.endAt) {
    return syncFieldErrors.endAt;
  }

  if (!syncWindow.startAt || !syncWindow.endAt) {
    return 'Start time and end time are required.';
  }

  if (syncWindow.endAt.getTime() <= syncWindow.startAt.getTime()) {
    return 'End time must be later than start time.';
  }

  return '';
});
const settingsUser = computed(() => auth.user);
const settingsAvatarLabel = computed(() => {
  const source = profileForm.displayName.trim() || settingsUser.value?.principal || 'U';
  return source.charAt(0).toUpperCase();
});

const getConnectorLogo = (connectorId: string) => {
  if (connectorId === 'garmin-connect') {
    return garminConnectLogo;
  }

  return '';
};

const getTaskConnectorIcon = (connectorId: string) => {
  if (connectorId === 'plaid') {
    return 'pi pi-wallet';
  }

  if (connectorId === 'alpha-vantage') {
    return 'pi pi-chart-line';
  }

  return 'pi pi-link';
};

const formatConnectorTaskDateTime = (value: string | null) => {
  return value ?? '-';
};

const copyTaskJobId = async (jobId: string) => {
  const fallbackCopy = () => {
    const textarea = document.createElement('textarea');
    textarea.value = jobId;
    textarea.setAttribute('readonly', 'true');
    textarea.style.position = 'absolute';
    textarea.style.left = '-9999px';
    document.body.appendChild(textarea);
    textarea.select();
    const copied = document.execCommand('copy');
    document.body.removeChild(textarea);
    return copied;
  };

  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(jobId);
      showToast('success', 'Job ID copied.');
      return;
    }

    if (fallbackCopy()) {
      showToast('success', 'Job ID copied.');
      return;
    }
  } catch {
    if (fallbackCopy()) {
      showToast('success', 'Job ID copied.');
      return;
    }
  }

  showToast('error', 'Unable to copy Job ID.');
};

const syncProfileForm = (user: AuthUser | null) => {
  profileForm.displayName = String(user?.displayName ?? '');
  profileForm.email = String(user?.email ?? '');
  profileForm.phone = String(user?.phone ?? '');
  profileForm.avatarUrl = String(user?.avatarUrl ?? '');
};

const resetPasswordForm = () => {
  passwordForm.currentPassword = '';
  passwordForm.newPassword = '';
  passwordForm.confirmPassword = '';
};

const getStatusLabel = (status: ConnectorStatus) => {
  if (status === 'running') {
    return 'Running';
  }

  if (status === 'stopped') {
    return 'Stopped';
  }

  return 'Not Configured';
};

const getStatusClass = (status: ConnectorStatus) => {
  if (status === 'running') {
    return 'connector-status-running';
  }

  if (status === 'stopped') {
    return 'connector-status-stopped';
  }

  return 'connector-status-not-configured';
};

const getConnectorTaskStatusLabel = (status: ConnectorTaskStatus) => {
  if (status === 'queued') {
    return 'Pending';
  }

  if (status === 'running') {
    return 'In Progress';
  }

  if (status === 'success') {
    return 'Completed';
  }

  return 'Failed';
};

const getConnectorTaskStatusClass = (status: ConnectorTaskStatus) => {
  if (status === 'queued') {
    return 'connector-task-status-pending';
  }

  if (status === 'running') {
    return 'connector-task-status-running';
  }

  if (status === 'success') {
    return 'connector-task-status-completed';
  }

  return 'connector-task-status-failed';
};

const formatTriggerType = (triggerType: ConnectorTaskTriggerType) => {
  return triggerType === 'scheduled' ? 'Scheduled' : 'Manual';
};

const getConnectorTaskDomainLabel = (domain: ConnectorTaskDomain) => {
  return domain === 'health' ? 'Health' : 'Finance';
};

const getConnectorTaskPeriodLabel = (period: SyncJobPeriod) => {
  if (period === 'yesterday') {
    return 'Yesterday';
  }

  if (period === 'last_7_days') {
    return 'Last 7 days';
  }

  return 'Last 30 days';
};

const resetConnectorTaskFilters = () => {
  connectorTaskFilters.status = 'all';
  connectorTaskFilters.triggerType = 'all';
  connectorTaskFilters.domain = 'all';
  connectorTaskPeriod.value = null;
};

const setConnectorTaskStatusFilter = (status: ConnectorTaskStatus) => {
  connectorTaskFilters.status = status;
};

const setConnectorTaskTriggerTypeFilter = (triggerType: ConnectorTaskTriggerType) => {
  connectorTaskFilters.triggerType = triggerType;
};

const setConnectorTaskDomainFilter = (domain: ConnectorTaskDomain) => {
  connectorTaskFilters.domain = domain;
};

const setConnectorTaskPeriodFilter = (period: SyncJobPeriod) => {
  connectorTaskPeriod.value = period;
};

const canToggleConnectorStatus = (connector: ConnectorRecord) => connector.status !== 'not_configured';

const isStatusUpdating = (connectorId: ConnectorRecord['id']) => {
  return connectorStatusUpdatingIds.value.includes(connectorId);
};

const syncDraftConfig = (source: ConnectorConfigValues) => {
  for (const key of Object.keys(connectorDraftConfig)) {
    delete connectorDraftConfig[key];
  }

  Object.assign(connectorDraftConfig, source);
};

const openAvatarPicker = () => {
  avatarInputRef.value?.click();
};

const removeAvatar = () => {
  profileForm.avatarUrl = '';
  if (avatarInputRef.value) {
    avatarInputRef.value.value = '';
  }
};

const handleAvatarSelected = (event: Event) => {
  const input = event.target as HTMLInputElement | null;
  const file = input?.files?.[0];

  if (!file) {
    return;
  }

  if (!file.type.startsWith('image/')) {
    showToast('error', 'Please choose an image file.');
    input.value = '';
    return;
  }

  const reader = new FileReader();
  reader.onload = () => {
    profileForm.avatarUrl = typeof reader.result === 'string' ? reader.result : '';
  };
  reader.readAsDataURL(file);
};

const loadConnectors = async () => {
  connectorLoading.value = true;
  connectorLoadError.value = '';

  const result = await connectorApi.list(auth.token);
  connectorLoading.value = false;

  if (!result.success || !result.data) {
    connectorLoadError.value = result.message ?? 'Unable to load connectors.';
    return;
  }

  connectorRecords.value = result.data;
};

const saveProfile = async () => {
  if (!profileForm.displayName.trim()) {
    showToast('error', 'Nickname is required.');
    return;
  }

  if (profileForm.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(profileForm.email.trim())) {
    showToast('error', 'Please enter a valid email address.');
    return;
  }

  profileSaving.value = true;
  const result = await auth.updateProfile({
    displayName: profileForm.displayName.trim(),
    email: profileForm.email.trim(),
    phone: profileForm.phone.trim(),
    avatarUrl: profileForm.avatarUrl.trim()
  });
  profileSaving.value = false;

  if (!result.success) {
    showToast('error', result.message ?? 'Unable to update profile.');
    return;
  }

  syncProfileForm(result.data ?? null);
  showToast('success', result.message ?? 'Profile updated successfully.');
};

const updatePassword = async () => {
  if (!passwordForm.currentPassword || !passwordForm.newPassword || !passwordForm.confirmPassword) {
    showToast('error', 'All password fields are required.');
    return;
  }

  if (passwordForm.newPassword.length < 6) {
    showToast('error', 'New password must be at least 6 characters.');
    return;
  }

  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    showToast('error', 'Passwords do not match.');
    return;
  }

  passwordSaving.value = true;
  const result = await auth.changePassword({
    currentPassword: passwordForm.currentPassword,
    newPassword: passwordForm.newPassword,
    confirmPassword: passwordForm.confirmPassword
  });
  passwordSaving.value = false;

  if (!result.success) {
    showToast('error', result.message ?? 'Unable to update password.');
    return;
  }

  resetPasswordForm();
  showToast('success', result.message ?? 'Password updated successfully.');
};

const openConnectorDialog = (connector: ConnectorRecord) => {
  const definition = getConnectorDefinition(connector.id);
  const initialConfig = Object.fromEntries(
    (definition?.fields ?? []).map((field) => [field.key, connector.config[field.key] ?? ''])
  );

  selectedConnectorId.value = connector.id;
  connectorDraftSchedule.value = connector.schedule;
  syncDraftConfig(initialConfig);
  connectorDialogVisible.value = true;
};

const resetConnectorDialog = () => {
  selectedConnectorId.value = null;
  connectorDraftSchedule.value = '';
  connectorTesting.value = false;
  connectorSaving.value = false;
  syncDraftConfig({});
};

const closeConnectorDialog = () => {
  connectorDialogVisible.value = false;
};

const validateSyncWindowField = (field: SyncWindowField, rawValue = syncInputValues[field]) => {
  const trimmedValue = rawValue.trim();
  const fieldLabel = syncFieldLabelMap[field];

  if (!trimmedValue) {
    syncWindow[field] = null;
    syncInputValues[field] = '';
    syncFieldErrors[field] = `${fieldLabel} is required.`;
    return false;
  }

  const parsedResult = parseDateTime(trimmedValue);
  if (!parsedResult.success || !parsedResult.date) {
    syncFieldErrors[field] = parsedResult.message ?? `${fieldLabel} must use YYYY-MM-DD HH:MM:SS format.`;
    return false;
  }

  syncWindow[field] = parsedResult.date;
  syncInputValues[field] = formatDateTime(parsedResult.date);
  syncFieldErrors[field] = '';
  return true;
};

const handleSyncDateInput = (field: SyncWindowField, event: Event) => {
  syncInputValues[field] = (event.target as HTMLInputElement | null)?.value ?? '';

  if (syncFieldErrors[field]) {
    syncFieldErrors[field] = '';
  }
};

const handleSyncDateBlur = (field: SyncWindowField, event: DatePickerBlurEvent) => {
  syncInputValues[field] = String(event.value ?? '');
  validateSyncWindowField(field);
};

const handleSyncDateModelUpdate = (
  field: SyncWindowField,
  value: Date | Date[] | (Date | null)[] | null | undefined
) => {
  const normalizedValue = value instanceof Date ? value : null;

  syncWindow[field] = normalizedValue;
  syncInputValues[field] = normalizedValue ? formatDateTime(normalizedValue) : '';
  syncFieldErrors[field] = normalizedValue ? '' : `${syncFieldLabelMap[field]} is required.`;
};

const openSyncDialog = (connector: ConnectorRecord) => {
  const defaultStart = getShanghaiStartOfDay(-3);
  const defaultEnd = getShanghaiStartOfDay(0);

  selectedSyncConnectorId.value = connector.id;
  syncWindow.startAt = defaultStart;
  syncWindow.endAt = defaultEnd;
  syncInputValues.startAt = formatDateTime(defaultStart);
  syncInputValues.endAt = formatDateTime(defaultEnd);
  syncFieldErrors.startAt = '';
  syncFieldErrors.endAt = '';
  syncDialogVisible.value = true;
};

const resetSyncDialog = () => {
  selectedSyncConnectorId.value = null;
  syncWindow.startAt = null;
  syncWindow.endAt = null;
  syncInputValues.startAt = '';
  syncInputValues.endAt = '';
  syncFieldErrors.startAt = '';
  syncFieldErrors.endAt = '';
  syncSubmitting.value = false;
};

const closeSyncDialog = () => {
  syncDialogVisible.value = false;
};

const testConnectorConnection = async () => {
  if (!selectedConnector.value) {
    return null;
  }

  connectorTesting.value = true;

  const result = await connectorApi.testConnection(auth.token, {
    id: selectedConnector.value.id,
    config: { ...connectorDraftConfig }
  });

  connectorTesting.value = false;
  showToast(result.success ? 'success' : 'error', result.message ?? (result.success ? 'Connection verified successfully.' : 'Connection test failed.'));

  return result;
};

const saveConnectorConfiguration = async () => {
  if (!selectedConnector.value) {
    return;
  }

  if (scheduleError.value) {
    showToast('error', scheduleError.value);
    return;
  }

  connectorSaving.value = true;

  const saveResult = await connectorApi.saveConfiguration(auth.token, {
    id: selectedConnector.value.id,
    schedule: connectorDraftSchedule.value.trim(),
    config: { ...connectorDraftConfig }
  });

  connectorSaving.value = false;

  if (!saveResult.success || !saveResult.data) {
    showToast('error', saveResult.message ?? 'Unable to save connector configuration.');
    return;
  }

  const connectorIndex = connectorRecords.value.findIndex((connector) => connector.id === saveResult.data?.id);
  if (connectorIndex >= 0) {
    connectorRecords.value.splice(connectorIndex, 1, saveResult.data);
  }

  showToast('success', saveResult.message ?? `${saveResult.data.name} configuration saved.`);
  closeConnectorDialog();
};

const confirmSync = async () => {
  if (!selectedSyncConnector.value) {
    return;
  }

  const hasValidStartAt = validateSyncWindowField('startAt');
  const hasValidEndAt = validateSyncWindowField('endAt');
  if (!hasValidStartAt || !hasValidEndAt) {
    showToast('error', syncWindowError.value || 'Please enter a valid sync time range.');
    return;
  }

  if (syncWindowError.value) {
    showToast('error', syncWindowError.value);
    return;
  }

  syncSubmitting.value = true;

  const result = await connectorApi.createSyncJob(auth.token, {
    id: selectedSyncConnector.value.id,
    startAt: formatDateTime(syncWindow.startAt as Date),
    endAt: formatDateTime(syncWindow.endAt as Date)
  });

  syncSubmitting.value = false;

  if (!result.success || !result.data) {
    showToast('error', result.message ?? 'Unable to queue sync job.');
    return;
  }

  showToast('success', result.message ?? `${selectedSyncConnector.value.name} sync job queued.`);
  closeSyncDialog();
};

const toggleConnectorStatus = async (connector: ConnectorRecord, enabled: boolean) => {
  if (!canToggleConnectorStatus(connector) || isStatusUpdating(connector.id)) {
    return;
  }

  connectorStatusUpdatingIds.value.push(connector.id);

  const result = await connectorApi.updateStatus(auth.token, {
    id: connector.id,
    status: enabled ? 'running' : 'stopped'
  });

  connectorStatusUpdatingIds.value = connectorStatusUpdatingIds.value.filter((id) => id !== connector.id);

  if (!result.success || !result.data) {
    showToast('error', result.message ?? 'Unable to update connector status.');
    return;
  }

  const connectorIndex = connectorRecords.value.findIndex((item) => item.id === result.data?.id);
  if (connectorIndex >= 0) {
    connectorRecords.value.splice(connectorIndex, 1, result.data);
  }

  showToast('success', result.message ?? `${result.data.name} status updated.`);
};

const onConnectorTabChange = (event: TabChangeEvent) => {
  connectorActiveIndex.value = event.index;
};

watch(
  () => connectorTaskSearch.value,
  (value) => {
    if (connectorTaskSearchDebounceTimer) {
      clearTimeout(connectorTaskSearchDebounceTimer);
    }

    connectorTaskSearchDebounceTimer = setTimeout(() => {
      connectorTaskSearchQuery.value = value.trim();
    }, 300);
  },
  { immediate: true }
);

watch(
  () => activeTab.value,
  (tab) => {
    if (tab === 'connector-tasks') {
      reloadConnectorTasks();
    }
  },
  { immediate: true }
);

watch(
  [
    () => connectorTaskFilters.status,
    () => connectorTaskFilters.triggerType,
    () => connectorTaskFilters.domain,
    () => connectorTaskPeriod.value,
    () => connectorTaskSearchQuery.value
  ],
  () => {
    if (activeTab.value === 'connector-tasks') {
      reloadConnectorTasks();
    }
  }
);

onBeforeUnmount(() => {
  if (connectorTaskSearchDebounceTimer) {
    clearTimeout(connectorTaskSearchDebounceTimer);
  }
});

onMounted(() => {
  loadConnectors();
});

watch(
  () => auth.user,
  (user) => {
    syncProfileForm(user);
  },
  { immediate: true }
);
</script>

<template>
  <section :class="activeTab === 'connector-tasks' ? 'tasks-page-section' : 'space-y-6'">
    <div v-if="activeTab === 'connector-settings'" class="app-panel connector-panel p-4 sm:p-5">
      <TabMenu
        :model="connectorTabItems"
        :active-index="connectorActiveIndex"
        class="connector-tabmenu"
        @tab-change="onConnectorTabChange"
      />

      <Message v-if="connectorLoadError" severity="error" class="mt-4" :closable="false">
        {{ connectorLoadError }}
      </Message>

      <DataTable :value="connectorRows" :loading="connectorLoading" class="mt-4 connector-table" table-style="min-width: 100%">
        <Column header="Connector">
          <template #body="{ data }">
            <div class="connector-identity">
              <img :src="getConnectorLogo(data.id)" :alt="data.name" class="connector-brand-logo" />
              <span class="connector-name">{{ data.name }}</span>
            </div>
          </template>
        </Column>
        <Column field="schedule" header="Schudule" />
        <Column field="lastRun" header="Last Run" />
        <Column field="nextRun" header="Next Run" />
        <Column header="Status">
          <template #body="{ data }">
            <div class="connector-status-cell">
              <span class="connector-status-pill" :class="getStatusClass(data.status)">
                {{ getStatusLabel(data.status) }}
              </span>
              <ToggleSwitch
                :model-value="data.status === 'running'"
                :disabled="!canToggleConnectorStatus(data) || isStatusUpdating(data.id)"
                :aria-label="`Toggle ${data.name} status`"
                @update:model-value="toggleConnectorStatus(data, Boolean($event))"
              />
            </div>
          </template>
        </Column>
        <Column header="Actions">
          <template #body="{ data }">
            <div class="connector-actions">
              <Button label="Configure" size="small" severity="secondary" outlined @click="openConnectorDialog(data)" />
              <Button label="Sync" size="small" @click="openSyncDialog(data)" />
            </div>
          </template>
        </Column>
        <template #empty>
          <div class="connector-empty">No Connector Available</div>
        </template>
      </DataTable>
    </div>

    <div v-else-if="activeTab === 'connector-tasks'" class="app-panel connector-tasks-panel p-4 sm:px-5 sm:pt-5 sm:pb-3">
      <div class="connector-tasks-layout">
        <aside class="connector-tasks-sidebar">
          <div class="connector-tasks-filter-list">
            <button
              type="button"
              class="connector-tasks-filter connector-tasks-filter-primary"
              :class="{ 'connector-tasks-filter-active': isAllConnectorTasksSelected }"
              @click="resetConnectorTaskFilters"
            >
              <span class="connector-tasks-filter-main">
                <i class="pi pi-list connector-tasks-filter-icon" />
                <span class="connector-tasks-filter-label">All Tasks</span>
              </span>
              <span class="connector-tasks-filter-count">
                {{ connectorTaskFacets.allTasks }}
              </span>
            </button>
          </div>

          <div class="connector-tasks-filter-group">
            <p class="connector-tasks-filter-group-title">Status</p>
            <div class="connector-tasks-filter-list connector-tasks-filter-list-grouped">
              <button
                v-for="item in connectorTaskStatusItems"
                :key="item.key"
                type="button"
                class="connector-tasks-filter connector-tasks-filter-secondary"
                :class="{ 'connector-tasks-filter-active': connectorTaskFilters.status === item.key }"
                @click="setConnectorTaskStatusFilter(item.key)"
              >
                <span class="connector-tasks-filter-main">
                  <i :class="[item.icon, 'connector-tasks-filter-icon']" />
                  <span class="connector-tasks-filter-label">{{ item.label }}</span>
                </span>
                <span class="connector-tasks-filter-count">{{ item.count }}</span>
              </button>
            </div>
          </div>

          <div class="connector-tasks-filter-group">
            <p class="connector-tasks-filter-group-title">Trigger Type</p>
            <div class="connector-tasks-filter-list connector-tasks-filter-list-grouped">
              <button
                v-for="item in connectorTaskTriggerTypeItems"
                :key="item.key"
                type="button"
                class="connector-tasks-filter connector-tasks-filter-secondary"
                :class="{ 'connector-tasks-filter-active': connectorTaskFilters.triggerType === item.key }"
                @click="setConnectorTaskTriggerTypeFilter(item.key)"
              >
                <span class="connector-tasks-filter-main">
                  <i :class="[item.icon, 'connector-tasks-filter-icon']" />
                  <span class="connector-tasks-filter-label">{{ item.label }}</span>
                </span>
                <span class="connector-tasks-filter-count">{{ item.count }}</span>
              </button>
            </div>
          </div>

          <div class="connector-tasks-filter-group">
            <p class="connector-tasks-filter-group-title">Domain</p>
            <div class="connector-tasks-filter-list connector-tasks-filter-list-grouped">
              <button
                v-for="item in connectorTaskDomainItems"
                :key="item.key"
                type="button"
                class="connector-tasks-filter connector-tasks-filter-secondary"
                :class="{ 'connector-tasks-filter-active': connectorTaskFilters.domain === item.key }"
                @click="setConnectorTaskDomainFilter(item.key)"
              >
                <span class="connector-tasks-filter-main">
                  <i :class="[item.icon, 'connector-tasks-filter-icon']" />
                  <span class="connector-tasks-filter-label">{{ item.label }}</span>
                </span>
                <span class="connector-tasks-filter-count">{{ item.count }}</span>
              </button>
            </div>
          </div>

          <div class="connector-tasks-filter-group">
            <p class="connector-tasks-filter-group-title">Period</p>
            <div class="connector-tasks-filter-list connector-tasks-filter-list-grouped">
              <button
                v-for="item in connectorTaskPeriodItems"
                :key="item.key"
                type="button"
                class="connector-tasks-filter connector-tasks-filter-secondary"
                :class="{ 'connector-tasks-filter-active': connectorTaskPeriod === item.key }"
                @click="setConnectorTaskPeriodFilter(item.key)"
              >
                <span class="connector-tasks-filter-main">
                  <i :class="[item.icon, 'connector-tasks-filter-icon']" />
                  <span class="connector-tasks-filter-label">{{ item.label }}</span>
                </span>
                <span class="connector-tasks-filter-count">{{ item.count }}</span>
              </button>
            </div>
          </div>
        </aside>

        <div class="connector-tasks-content">
          <div class="connector-tasks-content-head">
            <IconField class="connector-tasks-search">
              <InputIcon class="pi pi-search" />
              <InputText
                v-model.trim="connectorTaskSearch"
                type="search"
                placeholder="Search connector"
                aria-label="Search connector"
              />
            </IconField>
            <div class="connector-tasks-head-actions">
              <span class="connector-tasks-selection">{{ activeConnectorTaskFilterLabel }}</span>
            </div>
          </div>

          <div v-if="connectorTaskLoading" class="connector-empty mt-4">Loading tasks...</div>
          <div v-else-if="connectorTaskLoadError" class="connector-task-load-error mt-4">
            <span>{{ connectorTaskLoadError }}</span>
            <Button label="Retry" size="small" severity="secondary" outlined @click="reloadConnectorTasks" />
          </div>
          <div v-else-if="connectorTaskRecords.length" class="connector-task-list mt-4">
            <article v-for="task in connectorTaskRecords" :key="task.jobId" class="connector-task-card">
              <div class="connector-task-card-head">
                <div class="connector-task-card-head-main">
                  <div class="connector-task-card-topline">
                    <div class="connector-task-card-heading">
                      <div class="connector-task-card-identity">
                        <span class="connector-task-card-logo-wrap">
                          <img
                            v-if="getConnectorLogo(task.connectorId)"
                            :src="getConnectorLogo(task.connectorId)"
                            :alt="task.connectorName"
                            class="connector-task-card-logo"
                          />
                          <i v-else :class="[getTaskConnectorIcon(task.connectorId), 'connector-task-card-logo-icon']" />
                        </span>
                        <h3 class="connector-task-card-title">{{ task.connectorName }}</h3>
                      </div>
                      <div class="connector-task-id-inline">
                        <span class="connector-task-job-id">{{ task.jobId }}</span>
                        <button
                          type="button"
                          class="connector-task-copy-button"
                          :aria-label="`Copy Job ID ${task.jobId}`"
                          @click="copyTaskJobId(task.jobId)"
                        >
                          <i class="pi pi-copy" />
                        </button>
                      </div>
                    </div>
                    <span class="connector-task-card-chip">{{ getConnectorTaskDomainLabel(task.domain) }}</span>
                    <span class="connector-task-card-chip">{{ formatTriggerType(task.triggerType) }}</span>
                  </div>
                </div>
                <span class="connector-status-pill" :class="getConnectorTaskStatusClass(task.status)">
                  {{ getConnectorTaskStatusLabel(task.status) }}
                </span>
              </div>

              <div class="connector-task-card-body">
                <div class="connector-task-summary-grid">
                  <div class="connector-task-summary-item">
                    <span class="connector-task-meta-label">Window Start</span>
                    <span class="connector-task-summary-value">{{ formatConnectorTaskDateTime(task.windowStart) }}</span>
                  </div>
                  <div class="connector-task-summary-item">
                    <span class="connector-task-meta-label">Window End</span>
                    <span class="connector-task-summary-value">{{ formatConnectorTaskDateTime(task.windowEnd) }}</span>
                  </div>
                  <div class="connector-task-summary-item">
                    <span class="connector-task-meta-label">Started At</span>
                    <span class="connector-task-summary-value">{{ formatConnectorTaskDateTime(task.startedAt) }}</span>
                  </div>
                  <div class="connector-task-summary-item">
                    <span class="connector-task-meta-label">Finished At</span>
                    <span class="connector-task-summary-value">{{ formatConnectorTaskDateTime(task.finishedAt) }}</span>
                  </div>
                  <div class="connector-task-summary-item connector-task-summary-item--count">
                    <span class="connector-task-stat-label">Fetched</span>
                    <span class="connector-task-summary-value connector-task-summary-value--count">
                      {{ task.fetchedCount }}
                    </span>
                  </div>
                  <div class="connector-task-summary-item connector-task-summary-item--count">
                    <span class="connector-task-stat-label">Inserted</span>
                    <span class="connector-task-summary-value connector-task-summary-value--count">
                      {{ task.insertedCount }}
                    </span>
                  </div>
                  <div class="connector-task-summary-item connector-task-summary-item--count">
                    <span class="connector-task-stat-label">Updated</span>
                    <span class="connector-task-summary-value connector-task-summary-value--count">
                      {{ task.updatedCount }}
                    </span>
                  </div>
                  <div class="connector-task-summary-item connector-task-summary-item--count">
                    <span class="connector-task-stat-label">Deduped</span>
                    <span class="connector-task-summary-value connector-task-summary-value--count">
                      {{ task.dedupedCount }}
                    </span>
                  </div>
                </div>

                <div v-if="task.status === 'failed'" class="connector-task-error-row">
                  <span class="connector-task-meta-label">Error Message</span>
                  <span class="connector-task-error" :class="{ 'connector-task-error-muted': !task.errorMessage }">
                    {{ task.errorMessage || 'Unknown error' }}
                  </span>
                </div>
              </div>
            </article>
          </div>
          <div v-else class="connector-empty mt-4">No Task Available</div>
          <div v-if="hasMoreConnectorTasks" class="connector-task-load-more">
            <Button
              label="Load More"
              severity="secondary"
              outlined
              :loading="connectorTaskLoadingMore"
              @click="loadMoreConnectorTasks"
            />
          </div>
        </div>
      </div>
    </div>

    <div v-else-if="activeTab === 'settings'" class="space-y-6">
      <div class="app-panel p-6 sm:p-8">
        <div class="settings-header">
          <div>
            <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 dark:text-slate-500">Profile</p>
            <h2 class="mt-2 text-2xl font-semibold text-slate-900 dark:text-slate-100">Account Settings</h2>
            <p class="mt-2 text-sm text-slate-500 dark:text-slate-400">
              Manage your profile details, avatar, and password for the OTW workspace.
            </p>
          </div>
        </div>

        <div class="mt-8 grid gap-8 xl:grid-cols-[18rem_minmax(0,1fr)]">
          <div class="settings-avatar-panel">
            <div class="settings-avatar-frame">
              <img v-if="profileForm.avatarUrl" :src="profileForm.avatarUrl" alt="User avatar" class="settings-avatar-image" />
              <Avatar v-else :label="settingsAvatarLabel" size="xlarge" class="settings-avatar-fallback" />
            </div>
            <div class="space-y-3">
              <Button label="Upload Avatar" severity="secondary" outlined class="w-full" @click="openAvatarPicker" />
              <Button
                label="Remove Avatar"
                severity="secondary"
                text
                class="w-full"
                :disabled="!profileForm.avatarUrl"
                @click="removeAvatar"
              />
              <input
                ref="avatarInputRef"
                type="file"
                accept="image/*"
                class="hidden"
                @change="handleAvatarSelected"
              />
            </div>
          </div>

          <div class="grid gap-8">
            <div class="settings-section">
              <div class="settings-section-head">
                <h3 class="settings-section-title">Profile Information</h3>
                <p class="settings-section-help">Your username is fixed. Nickname, email, and phone can be updated.</p>
              </div>

              <div class="grid gap-4 md:grid-cols-2">
                <div class="space-y-1.5">
                  <label for="settings-username" class="auth-label">Username</label>
                  <InputText
                    id="settings-username"
                    :model-value="settingsUser?.principal ?? ''"
                    class="w-full"
                    disabled
                  />
                </div>

                <div class="space-y-1.5">
                  <label for="settings-nickname" class="auth-label">Nickname</label>
                  <InputText id="settings-nickname" v-model="profileForm.displayName" class="w-full" placeholder="Enter your nickname" />
                </div>

                <div class="space-y-1.5">
                  <label for="settings-email" class="auth-label">Email</label>
                  <InputText id="settings-email" v-model="profileForm.email" class="w-full" placeholder="name@example.com" />
                </div>

                <div class="space-y-1.5">
                  <label for="settings-phone" class="auth-label">Phone</label>
                  <InputText id="settings-phone" v-model="profileForm.phone" class="w-full" placeholder="+1 555 010 1234" />
                </div>
              </div>

              <div class="mt-6 flex justify-end">
                <Button label="Save Profile" :loading="profileSaving" @click="saveProfile" />
              </div>
            </div>

            <div class="settings-section">
              <div class="settings-section-head">
                <h3 class="settings-section-title">Change Password</h3>
                <p class="settings-section-help">Use a strong password with at least 6 characters.</p>
              </div>

              <div class="grid gap-4 md:grid-cols-3">
                <div class="space-y-1.5">
                  <label for="settings-current-password" class="auth-label">Current Password</label>
                  <Password
                    id="settings-current-password"
                    v-model="passwordForm.currentPassword"
                    class="w-full"
                    input-class="w-full"
                    :feedback="false"
                    toggle-mask
                    placeholder="Current password"
                  />
                </div>

                <div class="space-y-1.5">
                  <label for="settings-new-password" class="auth-label">New Password</label>
                  <Password
                    id="settings-new-password"
                    v-model="passwordForm.newPassword"
                    class="w-full"
                    input-class="w-full"
                    :feedback="false"
                    toggle-mask
                    placeholder="New password"
                  />
                </div>

                <div class="space-y-1.5">
                  <label for="settings-confirm-password" class="auth-label">Confirm New Password</label>
                  <Password
                    id="settings-confirm-password"
                    v-model="passwordForm.confirmPassword"
                    class="w-full"
                    input-class="w-full"
                    :feedback="false"
                    toggle-mask
                    placeholder="Confirm new password"
                  />
                </div>
              </div>

              <div class="mt-6 flex justify-end">
                <Button label="Update Password" :loading="passwordSaving" @click="updatePassword" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="app-panel p-6 sm:p-8">
      <p class="text-xs font-semibold uppercase tracking-wide text-slate-400 dark:text-slate-500">{{ currentLabel }}</p>
      <div class="mt-4 rounded-2xl border border-slate-200/80 bg-slate-50/80 p-10 text-center dark:border-slate-700 dark:bg-slate-800/70">
        <p class="text-2xl font-semibold text-slate-800 dark:text-slate-100">Feature under development.</p>
      </div>
    </div>

    <Dialog
      v-model:visible="connectorDialogVisible"
      modal
      :dismissable-mask="!connectorDialogBusy"
      :close-on-escape="!connectorDialogBusy"
      :class="['connector-config-dialog w-[min(92vw,40rem)]', { 'connector-config-dialog-busy': connectorDialogBusy }]"
      :header="selectedConnector ? `Configure ${selectedConnector.name}` : 'Configure Connector'"
      @hide="resetConnectorDialog"
    >
      <div class="space-y-5">
        <div v-if="selectedConnectorDefinition" class="space-y-4">
          <div>
            <p class="connector-section-title">Connection Settings</p>
            <p class="connector-section-help">
              Connector-specific fields are defined by the connector type. Other connectors can expose different credentials.
            </p>
          </div>

          <div class="grid gap-4">
            <div v-for="field in selectedConnectorDefinition.fields" :key="field.key" class="space-y-1.5">
              <label :for="`connector-field-${field.key}`" class="auth-label">{{ field.label }}</label>

              <InputText
                v-if="field.type === 'text'"
                :id="`connector-field-${field.key}`"
                :model-value="connectorDraftConfig[field.key] ?? ''"
                :placeholder="field.placeholder"
                class="w-full"
                :autocomplete="field.autocomplete"
                @update:model-value="connectorDraftConfig[field.key] = String($event ?? '')"
              />

              <Password
                v-else
                :id="`connector-field-${field.key}`"
                :model-value="connectorDraftConfig[field.key] ?? ''"
                :placeholder="field.placeholder"
                class="w-full"
                input-class="w-full"
                :feedback="false"
                toggle-mask
                :autocomplete="field.autocomplete"
                @update:model-value="connectorDraftConfig[field.key] = String($event ?? '')"
              />
            </div>
          </div>
        </div>

        <div class="space-y-4 rounded-2xl border border-slate-200/80 bg-slate-50/70 p-4 dark:border-slate-700 dark:bg-slate-900/70">
          <div>
            <p class="connector-section-title">Update Schedule</p>
            <p class="connector-section-help">Use standard crontab syntax such as `0 2 * * *`.</p>
          </div>

          <div class="space-y-1.5">
            <label for="connector-schedule" class="auth-label">Update Frequency</label>
            <InputText
              id="connector-schedule"
              v-model="connectorDraftSchedule"
              class="w-full"
              placeholder="0 2 * * *"
              :invalid="Boolean(scheduleError)"
            />
            <small v-if="scheduleError" class="auth-error">{{ scheduleError }}</small>
          </div>

          <div class="connector-next-run">
            <span class="connector-next-run-label">Next 3 Updates</span>
            <div class="connector-next-run-list">
              <span v-for="run in upcomingRunPreviews" :key="run" class="connector-next-run-value">{{ run }}</span>
              <span v-if="!upcomingRunPreviews.length" class="connector-next-run-value">Not available</span>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="connector-dialog-actions">
          <Button
            label="Test Connection"
            severity="secondary"
            outlined
            :loading="connectorTesting"
            :disabled="connectorSaving"
            @click="testConnectorConnection"
          />
          <Button
            label="Cancel"
            severity="secondary"
            text
            :disabled="connectorTesting || connectorSaving"
            @click="closeConnectorDialog"
          />
          <Button label="Confirm" :loading="connectorSaving" :disabled="connectorTesting" @click="saveConnectorConfiguration" />
        </div>
      </template>
    </Dialog>

    <Dialog
      v-model:visible="syncDialogVisible"
      modal
      :dismissable-mask="!syncSubmitting"
      :close-on-escape="!syncSubmitting"
      :class="['connector-config-dialog sync-config-dialog w-[min(92vw,34rem)]', { 'connector-config-dialog-busy': syncSubmitting }]"
      :header="selectedSyncConnector ? `Sync ${selectedSyncConnector.name}` : 'Sync Connector'"
      @hide="resetSyncDialog"
    >
      <div class="space-y-5">
        <div class="space-y-1">
          <p class="connector-section-title">Sync Time Range</p>
          <p class="connector-section-help">Select the start and end time in `YYYY-MM-DD HH:MM:SS` format.</p>
        </div>

        <div class="grid gap-4">
          <div class="space-y-1.5">
            <label for="sync-start-at" class="auth-label">Start Time</label>
            <DatePicker
              id="sync-start-at"
              v-model="syncWindow.startAt"
              class="w-full"
              input-class="w-full"
              placeholder="YYYY-MM-DD HH:MM:SS"
              date-format="yy-mm-dd"
              show-time
              show-seconds
              hour-format="24"
              show-icon
              icon-display="input"
              :manual-input="true"
              @update:model-value="handleSyncDateModelUpdate('startAt', $event)"
              @input="handleSyncDateInput('startAt', $event)"
              @blur="handleSyncDateBlur('startAt', $event)"
            />
          </div>

          <div class="space-y-1.5">
            <label for="sync-end-at" class="auth-label">End Time</label>
            <DatePicker
              id="sync-end-at"
              v-model="syncWindow.endAt"
              class="w-full"
              input-class="w-full"
              placeholder="YYYY-MM-DD HH:MM:SS"
              date-format="yy-mm-dd"
              show-time
              show-seconds
              hour-format="24"
              show-icon
              icon-display="input"
              :manual-input="true"
              @update:model-value="handleSyncDateModelUpdate('endAt', $event)"
              @input="handleSyncDateInput('endAt', $event)"
              @blur="handleSyncDateBlur('endAt', $event)"
            />
          </div>
        </div>

        <small v-if="syncWindowError" class="auth-error">{{ syncWindowError }}</small>
      </div>

      <template #footer>
        <div class="connector-dialog-actions">
          <Button
            label="Cancel"
            severity="secondary"
            text
            :disabled="syncSubmitting"
            @click="closeSyncDialog"
          />
          <Button label="Confirm" :loading="syncSubmitting" @click="confirmSync" />
        </div>
      </template>
    </Dialog>
  </section>
</template>

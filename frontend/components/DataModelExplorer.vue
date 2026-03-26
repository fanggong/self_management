<script setup lang="ts">
import { dbtModelApi } from '~/services/api/dbt';
import { resolveConnectorDisplayIdentity } from '~/services/connectors/display';
import { getDbtLayerClass, stripAnsi } from '~/services/dbt/presentation';
import { useAuthStore } from '~/stores/auth';
import type {
  DbtBatchRunStreamEvent,
  DbtBatchRunScopeType,
  DbtModelDetail,
  DbtModelLayer,
  DbtModelListItem,
  DbtSingleRunStreamEvent,
  RunDbtModelResult,
  RunDbtModelsByScopeItem,
  RunDbtModelsByScopeResult
} from '~/types/dbt';

type DataTablePageEvent = {
  page: number;
  rows: number;
  first: number;
};

type ScopeOption = {
  value: string;
  label: string;
  scopeType: DbtBatchRunScopeType;
};

type BatchRunDisplayState = 'queued' | 'running' | 'success' | 'failed';

type BatchRunDisplayItem = RunDbtModelsByScopeItem & {
  state: BatchRunDisplayState;
};

const DEFAULT_PAGE_SIZE = 10;

const props = defineProps<{
  layer: DbtModelLayer;
}>();

const auth = useAuthStore();
const { showToast } = useAppToast();

const layerMetaMap: Record<DbtModelLayer, { title: string; emptyText: string }> = {
  staging: {
    title: 'Staging Models',
    emptyText: 'No staging models found.'
  },
  intermediate: {
    title: 'Intermediate Models',
    emptyText: 'No intermediate models found.'
  },
  marts: {
    title: 'Marts Models',
    emptyText: 'No marts models found.'
  }
};

const searchQuery = ref('');
const debouncedSearchQuery = ref('');
const models = ref<DbtModelListItem[]>([]);
const scopeSourceModels = ref<DbtModelListItem[]>([]);
const loading = ref(false);
const loadError = ref('');
const pageState = reactive({
  page: 1,
  pageSize: DEFAULT_PAGE_SIZE
});

const detailDialogVisible = ref(false);
const detailLoading = ref(false);
const detailError = ref('');
const selectedDetailModel = ref<DbtModelListItem | null>(null);
const modelDetail = ref<DbtModelDetail | null>(null);
const modelDetailCache = reactive<Record<string, DbtModelDetail>>({});

const runDialogVisible = ref(false);
const selectedModel = ref<DbtModelListItem | null>(null);
const runningModelName = ref('');
const runBusy = ref(false);
const runErrorMessage = ref('');
const runResult = ref<RunDbtModelResult | null>(null);
const liveRunStdout = ref('');
const liveRunStderr = ref('');

const selectedScopeValues = ref<string[]>([]);
const batchRunDialogVisible = ref(false);
const batchRunBusy = ref(false);
const batchRunError = ref('');
const batchRunResult = ref<RunDbtModelsByScopeResult | null>(null);
const batchRunItems = ref<BatchRunDisplayItem[]>([]);
const batchLiveOutput = ref('');
const batchOutputDialogVisible = ref(false);
const selectedBatchOutput = ref<BatchRunDisplayItem | null>(null);

const runStdoutOutputRef = ref<HTMLElement | null>(null);
const runStderrOutputRef = ref<HTMLElement | null>(null);
const batchLiveOutputRef = ref<HTMLElement | null>(null);

let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null;
let activeLoadRequestId = 0;
let activeScopeLoadRequestId = 0;
let activeDetailRequestId = 0;

const layerMeta = computed(() => layerMetaMap[props.layer]);
const scopeType = computed<DbtBatchRunScopeType>(() => props.layer === 'staging' ? 'connector' : 'domain');
const scopeLabel = computed(() => scopeType.value === 'connector' ? 'Connector' : 'Domain');
const scopePlaceholder = computed(() => scopeType.value === 'connector' ? 'Select connectors' : 'Select domains');
const modelDetailTitle = computed(() => {
  if (!selectedDetailModel.value) {
    return 'Model Details';
  }

  return selectedDetailModel.value.name;
});
const detailConnector = computed(() => modelDetail.value?.connector ?? selectedDetailModel.value?.connector ?? null);
const detailConnectorIdentity = computed(() => resolveConnectorDisplayIdentity(detailConnector.value));
const detailDomain = computed(() => modelDetail.value?.domain ?? selectedDetailModel.value?.domain ?? null);
const detailLastUpdated = computed(() => modelDetail.value?.lastRunCompletedAt ?? selectedDetailModel.value?.lastRunCompletedAt ?? null);
const runStatusLabel = computed(() => {
  if (runBusy.value) {
    return 'Running';
  }

  if (!runResult.value) {
    return 'Ready';
  }

  return runResult.value.success ? 'Success' : 'Failed';
});
const runStatusClass = computed(() => {
  if (runBusy.value) {
    return 'border-amber-200 bg-amber-50 text-amber-700';
  }

  if (!runResult.value) {
    return 'border-slate-200 bg-slate-50 text-slate-600';
  }

  return runResult.value.success
    ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
    : 'border-rose-200 bg-rose-50 text-rose-700';
});

const modelDetailCacheKey = (layer: DbtModelLayer, modelName: string) => `${layer}:${modelName}`;
const resolveLayerClass = getDbtLayerClass;
const getConnectorIdentity = (value: string | null | undefined) => resolveConnectorDisplayIdentity(value);
const selectedBatchOutputTitle = computed(() => selectedBatchOutput.value?.modelName ?? 'Run Output');
const scopeOptions = computed<ScopeOption[]>(() => {
  const optionMap = new Map<string, ScopeOption>();

  for (const item of scopeSourceModels.value) {
    const value = scopeType.value === 'connector' ? item.connectorKey : item.domainKey;
    const label = scopeType.value === 'connector' ? item.connector : item.domain;
    if (!value || !label || optionMap.has(value)) {
      continue;
    }

    optionMap.set(value, {
      value,
      label,
      scopeType: scopeType.value
    });
  }

  return Array.from(optionMap.values()).sort((left, right) => left.label.localeCompare(right.label));
});
const batchScopeLabels = computed(() => {
  const optionMap = new Map(scopeOptions.value.map((option) => [option.value, option.label]));
  return selectedScopeValues.value
    .map((value) => optionMap.get(value) ?? value)
    .filter((value): value is string => Boolean(value));
});
const batchRunDisabled = computed(() => (
  runBusy.value
  || batchRunBusy.value
  || selectedScopeValues.value.length === 0
  || scopeOptions.value.length === 0
));
const hasSingleRunActivity = computed(() => (
  Boolean(runResult.value)
  || Boolean(runErrorMessage.value)
  || liveRunStdout.value.length > 0
  || liveRunStderr.value.length > 0
));
const plannedBatchModels = computed(() => {
  const selectedValues = new Set(selectedScopeValues.value);

  return scopeSourceModels.value
    .filter((item) => {
      const scopeValue = scopeType.value === 'connector' ? item.connectorKey : item.domainKey;
      if (!scopeValue) {
        return false;
      }
      return selectedValues.has(scopeValue);
    })
    .sort((left, right) => left.name.localeCompare(right.name));
});
const hasBatchRunActivity = computed(() => (
  Boolean(batchRunError.value)
  || Boolean(batchRunResult.value)
  || batchRunItems.value.length > 0
  || batchLiveOutput.value.length > 0
));
const canStartSingleRun = computed(() => Boolean(selectedModel.value) && !runBusy.value && !hasSingleRunActivity.value);
const canStartBatchRun = computed(() => (
  !batchRunBusy.value
  && plannedBatchModels.value.length > 0
  && !hasBatchRunActivity.value
));
const displayedRunStdout = computed(() => {
  if (liveRunStdout.value) {
    return liveRunStdout.value;
  }
  if (runResult.value?.stdout) {
    return stripAnsi(runResult.value.stdout);
  }
  return runBusy.value ? 'Waiting for stdout output...' : 'No stdout output.';
});
const displayedRunStderr = computed(() => {
  if (liveRunStderr.value) {
    return liveRunStderr.value;
  }
  if (runResult.value?.stderr) {
    return stripAnsi(runResult.value.stderr);
  }
  return runBusy.value ? 'Waiting for stderr output...' : 'No stderr output.';
});
const batchTotalModels = computed(() => batchRunResult.value?.totalModels ?? batchRunItems.value.length);
const batchSucceededCount = computed(() => (
  batchRunResult.value?.succeededCount
  ?? batchRunItems.value.filter((item) => item.state === 'success').length
));
const batchFailedCount = computed(() => (
  batchRunResult.value?.failedCount
  ?? batchRunItems.value.filter((item) => item.state === 'failed').length
));
const displayBatchTotalModels = computed(() => batchTotalModels.value || plannedBatchModels.value.length);

const resetSingleRunState = () => {
  runningModelName.value = '';
  runBusy.value = false;
  runErrorMessage.value = '';
  runResult.value = null;
  liveRunStdout.value = '';
  liveRunStderr.value = '';
};

const resetBatchRunState = () => {
  batchRunBusy.value = false;
  batchRunError.value = '';
  batchRunResult.value = null;
  batchRunItems.value = [];
  batchLiveOutput.value = '';
};

const scrollOutputToBottom = (target: { value: HTMLElement | null }) => {
  nextTick(() => {
    if (target.value) {
      target.value.scrollTop = target.value.scrollHeight;
    }
  });
};

const appendSingleRunLog = (event: Extract<DbtSingleRunStreamEvent, { type: 'log' }>) => {
  if (event.stream === 'stderr') {
    liveRunStderr.value += event.text;
    scrollOutputToBottom(runStderrOutputRef);
    return;
  }

  liveRunStdout.value += event.text;
  scrollOutputToBottom(runStdoutOutputRef);
};

const appendBatchLog = (event: Extract<DbtBatchRunStreamEvent, { type: 'log' }>) => {
  const prefix = event.targetModelName ? `[${event.targetModelName}] ` : '';
  batchLiveOutput.value += `${prefix}${event.text}`;
  scrollOutputToBottom(batchLiveOutputRef);
};

const createBatchRunDisplayItem = (
  item: {
    modelName: string;
    layer: DbtModelLayer;
    scopeKey: string | null;
    scopeLabel: string | null;
  },
  state: BatchRunDisplayState
): BatchRunDisplayItem => ({
  modelName: item.modelName,
  layer: item.layer,
  scopeKey: item.scopeKey,
  scopeLabel: item.scopeLabel,
  success: state === 'success',
  returncode: null,
  stdout: '',
  stderr: '',
  startedAt: null,
  finishedAt: null,
  code: null,
  message: null,
  state
});

const loadModels = async () => {
  const requestId = ++activeLoadRequestId;
  loading.value = true;
  loadError.value = '';

  const result = await dbtModelApi.list(auth.token, {
    layer: props.layer,
    search: debouncedSearchQuery.value
  });

  if (requestId !== activeLoadRequestId) {
    return;
  }

  if (!result.success || !result.data) {
    models.value = [];
    loadError.value = result.message ?? 'Unable to load dbt models.';
    loading.value = false;
    return;
  }

  models.value = result.data.items;
  loading.value = false;
};

const loadScopeSourceModels = async () => {
  const requestId = ++activeScopeLoadRequestId;
  const result = await dbtModelApi.list(auth.token, {
    layer: props.layer,
    search: ''
  });

  if (requestId !== activeScopeLoadRequestId) {
    return;
  }

  scopeSourceModels.value = result.success && result.data ? result.data.items : [];
};

const openModelDetailDialog = async (model: DbtModelListItem) => {
  selectedDetailModel.value = model;
  detailDialogVisible.value = true;
  detailLoading.value = true;
  detailError.value = '';
  modelDetail.value = null;

  const cacheKey = modelDetailCacheKey(model.layer, model.name);
  if (modelDetailCache[cacheKey]) {
    modelDetail.value = modelDetailCache[cacheKey];
    detailLoading.value = false;
    return;
  }

  const requestId = ++activeDetailRequestId;
  const result = await dbtModelApi.getDetail(auth.token, model.layer, model.name);

  if (requestId !== activeDetailRequestId) {
    return;
  }

  detailLoading.value = false;

  if (!result.success || !result.data) {
    detailError.value = result.message ?? 'Unable to load model details.';
    showToast('error', detailError.value);
    return;
  }

  modelDetailCache[cacheKey] = result.data;
  modelDetail.value = result.data;
};

const openRunDialog = async (model: DbtModelListItem) => {
  selectedModel.value = model;
  runDialogVisible.value = true;
  resetSingleRunState();
};

const startRun = async () => {
  if (!selectedModel.value || runBusy.value) {
    return;
  }

  const model = selectedModel.value;
  runningModelName.value = model.name;
  runBusy.value = true;
  runErrorMessage.value = '';
  runResult.value = null;
  liveRunStdout.value = '';
  liveRunStderr.value = '';

  const result = await dbtModelApi.streamRun(auth.token, {
    layer: model.layer,
    modelName: model.name
  }, (event) => {
    if (event.type === 'log') {
      appendSingleRunLog(event);
      return;
    }

    if (event.type === 'run_finished') {
      runResult.value = {
        success: event.success,
        returncode: event.returncode ?? 0,
        stdout: event.stdout,
        stderr: event.stderr,
        startedAt: event.startedAt,
        finishedAt: event.finishedAt
      };
      if (!event.success) {
        runErrorMessage.value = event.message ?? 'dbt model run failed.';
      }
    }
  });

  if (result.data) {
    runResult.value = result.data;
    const cacheKey = modelDetailCacheKey(model.layer, model.name);
    if (modelDetailCache[cacheKey]) {
      modelDetailCache[cacheKey] = {
        ...modelDetailCache[cacheKey],
        lastRunCompletedAt: result.data.finishedAt ?? modelDetailCache[cacheKey].lastRunCompletedAt
      };
      if (selectedDetailModel.value?.layer === model.layer && selectedDetailModel.value?.name === model.name && modelDetail.value) {
        modelDetail.value = modelDetailCache[cacheKey];
      }
    }
  }

  if (!result.success) {
    runErrorMessage.value = result.message ?? 'dbt model run failed.';
  } else if (result.data && !result.data.success) {
    runErrorMessage.value = result.message ?? 'dbt model run failed.';
  }

  if (result.success && result.data?.success) {
    showToast('success', result.message ?? `${model.name} run completed.`);
  } else {
    showToast('error', result.message ?? `${model.name} run failed.`);
  }

  runBusy.value = false;
  await loadModels();
  await loadScopeSourceModels();
};

const openBatchRunDialog = () => {
  batchRunDialogVisible.value = true;
  resetBatchRunState();
};

const startBatchRun = async () => {
  if (batchRunBusy.value || plannedBatchModels.value.length === 0) {
    return;
  }

  batchRunBusy.value = true;
  batchRunError.value = '';
  batchRunResult.value = null;
  batchRunItems.value = [];
  batchLiveOutput.value = '';

  const result = await dbtModelApi.streamRunBatch(auth.token, {
    layer: props.layer,
    scopeType: scopeType.value,
    scopeValues: [...selectedScopeValues.value]
  }, (event) => {
    if (event.type === 'batch_started') {
      batchRunItems.value = event.items.map((item) => createBatchRunDisplayItem(item, 'queued'));
      return;
    }

    if (event.type === 'target_started') {
      batchRunItems.value = batchRunItems.value.map((item) => (
        item.modelName === event.modelName
          ? { ...item, state: 'running' }
          : item
      ));
      batchLiveOutput.value += `\n>>> Running ${event.modelName}\n`;
      scrollOutputToBottom(batchLiveOutputRef);
      return;
    }

    if (event.type === 'log') {
      appendBatchLog(event);
      return;
    }

    if (event.type === 'target_finished') {
      batchRunItems.value = batchRunItems.value.map((item) => (
        item.modelName === event.item.modelName
          ? { ...event.item, state: event.item.success ? 'success' : 'failed' }
          : item
      ));
      return;
    }

    if (event.type === 'batch_finished') {
      batchRunResult.value = {
        layer: event.layer,
        scopeType: event.scopeType,
        scopeValues: event.scopeValues,
        totalModels: event.totalModels,
        succeededCount: event.succeededCount,
        failedCount: event.failedCount,
        items: event.items
      };
    }
  });

  batchRunBusy.value = false;

  if (!result.success || !result.data) {
    batchRunError.value = result.message ?? 'Unable to run selected dbt models.';
    showToast('error', batchRunError.value);
    return;
  }

  batchRunResult.value = result.data;
  if (result.data.failedCount > 0) {
    showToast('error', `${result.data.succeededCount} models succeeded and ${result.data.failedCount} failed.`);
  } else {
    showToast('success', `${result.data.succeededCount} models run completed.`);
  }

  await loadModels();
  await loadScopeSourceModels();
};

const onPage = (event: DataTablePageEvent) => {
  pageState.page = event.page + 1;
  pageState.pageSize = event.rows;
};

const openBatchOutputDialog = (item: BatchRunDisplayItem) => {
  selectedBatchOutput.value = item;
  batchOutputDialogVisible.value = true;
};

const resolveBatchItemStatusClass = (item: BatchRunDisplayItem) => {
  if (item.state === 'queued') {
    return 'border-slate-200 bg-slate-50 text-slate-600 dark:border-slate-700 dark:bg-slate-900/60 dark:text-slate-200';
  }
  if (item.state === 'running') {
    return 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900/80 dark:bg-amber-950/60 dark:text-amber-200';
  }
  return item.success
    ? 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900/80 dark:bg-emerald-950/60 dark:text-emerald-200'
    : 'border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-900/80 dark:bg-rose-950/60 dark:text-rose-200';
};

const resolveBatchItemStatusLabel = (item: BatchRunDisplayItem) => {
  if (item.state === 'queued') {
    return 'Queued';
  }
  if (item.state === 'running') {
    return 'Running';
  }
  return item.success ? 'Success' : 'Failed';
};

const canViewBatchOutput = (item: BatchRunDisplayItem) => item.state === 'success' || item.state === 'failed';

const formatEstimatedRowCount = (value: number | null | undefined) => {
  if (value == null || Number.isNaN(value)) {
    return 'Unavailable';
  }

  return new Intl.NumberFormat().format(value);
};

watch(
  searchQuery,
  (value) => {
    if (searchDebounceTimer) {
      clearTimeout(searchDebounceTimer);
    }

    searchDebounceTimer = setTimeout(() => {
      debouncedSearchQuery.value = value.trim();
    }, 220);
  }
);

watch(
  [() => props.layer, debouncedSearchQuery],
  () => {
    pageState.page = 1;
    loadModels();
  },
  { immediate: true }
);

watch(
  () => props.layer,
  () => {
    selectedScopeValues.value = [];
    batchRunDialogVisible.value = false;
    runDialogVisible.value = false;
    batchOutputDialogVisible.value = false;
    selectedBatchOutput.value = null;
    resetSingleRunState();
    resetBatchRunState();
    loadScopeSourceModels();
  },
  { immediate: true }
);

watch(
  scopeOptions,
  (options) => {
    const availableValues = new Set(options.map((option) => option.value));
    selectedScopeValues.value = selectedScopeValues.value.filter((value) => availableValues.has(value));
  }
);

onBeforeUnmount(() => {
  if (searchDebounceTimer) {
    clearTimeout(searchDebounceTimer);
  }
});
</script>

<template>
  <div class="space-y-4">
    <article class="h-[45rem] overflow-hidden rounded-2xl border border-slate-200/80 bg-white/90 shadow-sm transition-all duration-200 ease-out hover:border-slate-300/90 hover:shadow-md dark:border-slate-700 dark:bg-slate-900/70 dark:hover:border-slate-600">
      <div class="flex h-full flex-col overflow-hidden">
        <div class="border-b border-slate-200/80 px-5 py-2 dark:border-slate-800">
          <div class="flex flex-col gap-3 xl:min-h-[2.5rem] xl:flex-row xl:items-center xl:justify-between">
            <p class="text-sm font-semibold uppercase tracking-[0.18em] text-slate-500 dark:text-slate-400">
              {{ layerMeta.title }}
            </p>

            <div class="flex w-full flex-col gap-3 sm:flex-row xl:w-auto xl:items-center">
              <IconField class="w-full sm:flex-1 xl:w-[18rem] xl:flex-none">
                <InputIcon class="pi pi-search" />
                <InputText
                  v-model.trim="searchQuery"
                  type="search"
                  class="h-9 w-full text-sm"
                  placeholder="Search model name"
                  aria-label="Search model name"
                />
              </IconField>

              <MultiSelect
                v-model="selectedScopeValues"
                :options="scopeOptions"
                option-label="label"
                option-value="value"
                display="chip"
                filter
                :placeholder="scopePlaceholder"
                :max-selected-labels="2"
                selected-items-label="{0} selected"
                class="w-full sm:flex-1 xl:w-[18rem] xl:flex-none"
                :disabled="runBusy || batchRunBusy"
              >
                <template #option="{ option }">
                  <div v-if="option.scopeType === 'connector' && getConnectorIdentity(option.label)" class="connector-identity">
                    <img
                      v-if="getConnectorIdentity(option.label)?.logo"
                      :src="getConnectorIdentity(option.label)?.logo"
                      :alt="option.label"
                      class="connector-brand-logo"
                    />
                    <i
                      v-else
                      :class="[getConnectorIdentity(option.label)?.fallbackIcon, 'connector-brand-icon']"
                    />
                    <span class="connector-name">{{ option.label }}</span>
                  </div>
                  <span
                    v-else-if="option.scopeType === 'domain'"
                    class="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold text-slate-600 dark:border-slate-700 dark:bg-slate-900/60 dark:text-slate-200"
                  >
                    {{ option.label }}
                  </span>
                  <span v-else>{{ option.label }}</span>
                </template>
              </MultiSelect>

              <Button
                label="Run"
                class="shrink-0"
                :disabled="batchRunDisabled"
                :loading="batchRunBusy"
                @click="openBatchRunDialog"
              />
            </div>
          </div>
        </div>

        <div class="flex min-h-0 flex-1 flex-col gap-4 px-4 pb-4 pt-4 sm:px-5">
          <Message v-if="loadError" severity="error" :closable="false">
            {{ loadError }}
          </Message>

          <div class="min-h-0 flex-1 overflow-hidden">
            <DataTable
              :value="models"
              :loading="loading"
              :rows="pageState.pageSize"
              :first="(pageState.page - 1) * pageState.pageSize"
              :total-records="models.length"
              paginator
              scrollable
              scroll-height="flex"
              class="data-model-card-table h-full"
              paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport"
              current-page-report-template="{first} - {last} of {totalRecords}"
              table-style="min-width: 100%"
              striped-rows
              @page="onPage"
            >
      <Column header="Model Name">
        <template #body="{ data }">
          <button
            type="button"
            class="block max-w-[18rem] truncate font-mono text-[0.92rem] font-medium tracking-[0.01em] text-slate-700 transition hover:text-slate-950 dark:text-slate-200 dark:hover:text-white"
            :title="data.name"
            @click="openModelDetailDialog(data)"
          >
            {{ data.name }}
          </button>
        </template>
      </Column>

      <Column header="Description">
        <template #body="{ data }">
          <span
            class="block max-w-[26rem] truncate text-sm text-slate-600 dark:text-slate-300"
            :title="data.description || undefined"
          >
            {{ data.description || '—' }}
          </span>
        </template>
      </Column>

      <Column v-if="props.layer === 'staging'" header="Connector">
        <template #body="{ data }">
          <div v-if="getConnectorIdentity(data.connector)" class="connector-identity">
            <img
              v-if="getConnectorIdentity(data.connector)?.logo"
              :src="getConnectorIdentity(data.connector)?.logo"
              :alt="getConnectorIdentity(data.connector)?.name ?? (data.connector || 'Connector')"
              class="connector-brand-logo"
            />
            <i
              v-else
              :class="[getConnectorIdentity(data.connector)?.fallbackIcon, 'connector-brand-icon']"
            />
            <span class="connector-name">{{ data.connector }}</span>
          </div>
          <span v-else class="text-sm font-medium text-slate-600 dark:text-slate-300">
            {{ data.connector || '—' }}
          </span>
        </template>
      </Column>

      <Column v-else header="Domain">
        <template #body="{ data }">
          <span class="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold text-slate-600 dark:border-slate-700 dark:bg-slate-900/60 dark:text-slate-200">
            {{ data.domain || '—' }}
          </span>
        </template>
      </Column>

      <Column header="Last Updated">
        <template #body="{ data }">
          <span class="text-sm text-slate-600 dark:text-slate-300">
            {{ data.lastRunCompletedAt || 'Never run' }}
          </span>
        </template>
      </Column>

      <Column header="Actions">
        <template #body="{ data }">
          <Button
            label="Run"
            size="small"
            :loading="runBusy && runningModelName === data.name"
            :disabled="runBusy || batchRunBusy"
            @click="openRunDialog(data)"
          />
        </template>
      </Column>

      <template #empty>
        <div class="flex min-h-[18rem] items-center justify-center text-center text-sm text-slate-500 dark:text-slate-400">
          {{ layerMeta.emptyText }}
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
      :dismissable-mask="true"
      :close-on-escape="true"
      :header="modelDetailTitle"
      class="w-[min(94vw,72rem)]"
    >
      <div class="space-y-4">
        <Message v-if="detailLoading" severity="info" :closable="false">
          Loading model details.
        </Message>

        <Message v-else-if="detailError" severity="error" :closable="false">
          {{ detailError }}
        </Message>

        <template v-else-if="modelDetail">
          <div class="space-y-4 rounded-2xl border border-slate-200 bg-slate-50/80 p-4 dark:border-slate-700 dark:bg-slate-900/60">
            <div class="space-y-1">
              <p class="text-sm font-semibold text-slate-800 dark:text-slate-100">{{ modelDetail.name }}</p>
              <p class="text-sm leading-6 text-slate-600 dark:text-slate-300">
                {{ modelDetail.description || 'No description available.' }}
              </p>
            </div>

            <div class="grid gap-3 text-sm md:grid-cols-2 xl:grid-cols-4">
              <div class="rounded-xl border border-slate-200 bg-white px-3 py-2 dark:border-slate-700 dark:bg-slate-950/60">
                <p class="text-xs uppercase tracking-wide text-slate-400 dark:text-slate-500">Layer</p>
                <span
                  class="mt-2 inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.08em]"
                  :class="resolveLayerClass(modelDetail.layer)"
                >
                  {{ modelDetail.layer }}
                </span>
              </div>
              <div
                v-if="detailConnector"
                class="rounded-xl border border-slate-200 bg-white px-3 py-2 dark:border-slate-700 dark:bg-slate-950/60"
              >
                <p class="text-xs uppercase tracking-wide text-slate-400 dark:text-slate-500">Connector</p>
                <div v-if="detailConnectorIdentity" class="connector-identity mt-2">
                  <img
                    v-if="detailConnectorIdentity.logo"
                    :src="detailConnectorIdentity.logo"
                    :alt="detailConnectorIdentity.name"
                    class="connector-brand-logo"
                  />
                  <i
                    v-else
                    :class="[detailConnectorIdentity.fallbackIcon, 'connector-brand-icon']"
                  />
                  <span class="connector-name">{{ detailConnector }}</span>
                </div>
                <p v-else class="mt-1 font-medium text-slate-700 dark:text-slate-200">{{ detailConnector }}</p>
              </div>
              <div
                v-if="detailDomain"
                class="rounded-xl border border-slate-200 bg-white px-3 py-2 dark:border-slate-700 dark:bg-slate-950/60"
              >
                <p class="text-xs uppercase tracking-wide text-slate-400 dark:text-slate-500">Domain</p>
                <p class="mt-1 font-medium text-slate-700 dark:text-slate-200">{{ detailDomain }}</p>
              </div>
              <div class="rounded-xl border border-slate-200 bg-white px-3 py-2 dark:border-slate-700 dark:bg-slate-950/60">
                <p class="text-xs uppercase tracking-wide text-slate-400 dark:text-slate-500">Estimated Rows</p>
                <p class="mt-1 font-medium text-slate-700 dark:text-slate-200">{{ formatEstimatedRowCount(modelDetail.estimatedRowCount) }}</p>
              </div>
              <div class="rounded-xl border border-slate-200 bg-white px-3 py-2 dark:border-slate-700 dark:bg-slate-950/60">
                <p class="text-xs uppercase tracking-wide text-slate-400 dark:text-slate-500">Last Updated</p>
                <p class="mt-1 font-medium text-slate-700 dark:text-slate-200">{{ detailLastUpdated || 'Never run' }}</p>
              </div>
            </div>
          </div>

          <div class="rounded-2xl border border-slate-200 dark:border-slate-700">
            <div class="border-b border-slate-200 px-4 py-3 dark:border-slate-700">
              <p class="text-sm font-semibold text-slate-700 dark:text-slate-200">Fields</p>
            </div>

            <DataTable
              :value="modelDetail.columns"
              class="data-model-table"
              size="small"
              scrollable
              scroll-height="18rem"
              table-style="min-width: 100%"
              striped-rows
            >
              <Column header="Field">
                <template #body="{ data }">
                  <span class="block max-w-[16rem] truncate font-mono text-sm text-slate-700 dark:text-slate-200">
                    {{ data.name }}
                  </span>
                </template>
              </Column>

              <Column header="Type">
                <template #body="{ data }">
                  <span class="font-mono text-sm text-slate-600 dark:text-slate-300">
                    {{ data.type || '—' }}
                  </span>
                </template>
              </Column>

              <Column header="Description">
                <template #body="{ data }">
                  <span
                    class="block max-w-[36rem] truncate text-sm text-slate-600 dark:text-slate-300"
                    :title="data.description || undefined"
                  >
                    {{ data.description || '—' }}
                  </span>
                </template>
              </Column>

              <template #empty>
                <div class="py-8 text-center text-sm text-slate-500 dark:text-slate-400">
                  Field metadata is not available for this model.
                </div>
              </template>
            </DataTable>
          </div>
        </template>
      </div>
    </Dialog>

    <Dialog
      v-model:visible="runDialogVisible"
      modal
      :dismissable-mask="true"
      :close-on-escape="true"
      header="Model Run"
      class="w-[min(92vw,56rem)]"
    >
      <div class="space-y-4">
        <div class="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-slate-50/80 p-4 dark:border-slate-700 dark:bg-slate-900/60">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div class="space-y-1">
              <p class="text-sm font-medium text-slate-700 dark:text-slate-200">Current Run Status</p>
              <div class="flex flex-wrap items-center gap-2">
                <p class="text-xs text-slate-500 dark:text-slate-400">
                  {{ selectedModel?.name || 'No model selected' }}
                </p>
              </div>
            </div>
            <span class="inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold" :class="runStatusClass">
              <i v-if="runBusy" class="pi pi-spin pi-spinner" />
              <i v-else-if="runResult?.success" class="pi pi-check-circle" />
              <i v-else-if="runResult" class="pi pi-times-circle" />
              {{ runStatusLabel }}
            </span>
          </div>

          <div class="grid gap-3 text-sm md:grid-cols-3">
            <div class="rounded-xl border border-slate-200 bg-white px-3 py-2 dark:border-slate-700 dark:bg-slate-950/60">
              <p class="text-xs uppercase tracking-wide text-slate-400 dark:text-slate-500">Layer</p>
              <span
                v-if="selectedModel?.layer"
                class="mt-2 inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.08em]"
                :class="resolveLayerClass(selectedModel.layer)"
              >
                {{ selectedModel.layer }}
              </span>
              <p v-else class="mt-1 font-medium text-slate-700 dark:text-slate-200">Unknown</p>
            </div>
            <div class="rounded-xl border border-slate-200 bg-white px-3 py-2 dark:border-slate-700 dark:bg-slate-950/60">
              <p class="text-xs uppercase tracking-wide text-slate-400 dark:text-slate-500">Started At</p>
              <p class="mt-1 font-medium text-slate-700 dark:text-slate-200">{{ runResult?.startedAt || 'Pending' }}</p>
            </div>
            <div class="rounded-xl border border-slate-200 bg-white px-3 py-2 dark:border-slate-700 dark:bg-slate-950/60">
              <p class="text-xs uppercase tracking-wide text-slate-400 dark:text-slate-500">Finished At</p>
              <p class="mt-1 font-medium text-slate-700 dark:text-slate-200">{{ runResult?.finishedAt || 'Pending' }}</p>
            </div>
          </div>
        </div>

        <Message v-if="canStartSingleRun" severity="info" :closable="false">
          Review the selected model and click Start to begin the run.
        </Message>

        <Message v-if="runBusy" severity="warn" :closable="false">
          dbt is currently running this model and streaming live output. You can close the dialog, but the backend run will continue.
        </Message>

        <Message v-else-if="runResult?.success" severity="success" :closable="false">
          dbt run completed successfully with return code {{ runResult.returncode }}.
        </Message>

        <Message v-else-if="runResult" severity="error" :closable="false">
          {{ runErrorMessage || `dbt run failed with return code ${runResult.returncode}.` }}
        </Message>

        <Message v-if="runErrorMessage && !runResult" severity="error" :closable="false">
          {{ runErrorMessage }}
        </Message>

        <div v-if="runBusy || hasSingleRunActivity" class="space-y-3">
          <div class="rounded-2xl border border-slate-200 dark:border-slate-700">
            <pre ref="runStdoutOutputRef" class="max-h-64 overflow-auto bg-slate-950 px-4 py-3 text-xs leading-6 text-slate-100">{{ displayedRunStdout }}</pre>
          </div>

          <div class="rounded-2xl border border-slate-200 dark:border-slate-700">
            <pre ref="runStderrOutputRef" class="max-h-64 overflow-auto bg-slate-950 px-4 py-3 text-xs leading-6 text-rose-200">{{ displayedRunStderr }}</pre>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="flex w-full items-center justify-end gap-2">
          <Button label="Close" severity="secondary" outlined @click="runDialogVisible = false" />
          <Button
            v-if="canStartSingleRun"
            label="Start"
            @click="startRun"
          />
        </div>
      </template>
    </Dialog>

    <Dialog
      v-model:visible="batchRunDialogVisible"
      modal
      :dismissable-mask="true"
      :close-on-escape="true"
      header="Model Batch Run"
      class="w-[min(96vw,72rem)]"
    >
      <div class="space-y-4">
        <div class="space-y-4 rounded-2xl border border-slate-200 bg-slate-50/80 p-4 dark:border-slate-700 dark:bg-slate-900/60">
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div class="space-y-2">
              <p class="text-sm font-medium text-slate-700 dark:text-slate-200">Selected {{ scopeLabel }}</p>
              <div class="flex flex-wrap items-center gap-2">
                <span
                  class="inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.08em]"
                  :class="resolveLayerClass(props.layer)"
                >
                  {{ props.layer }}
                </span>
                <template v-for="label in batchScopeLabels" :key="label">
                  <div v-if="scopeType === 'connector' && getConnectorIdentity(label)" class="connector-identity rounded-full border border-slate-200 bg-white px-3 py-1 dark:border-slate-700 dark:bg-slate-950/60">
                    <img
                      v-if="getConnectorIdentity(label)?.logo"
                      :src="getConnectorIdentity(label)?.logo"
                      :alt="label"
                      class="connector-brand-logo"
                    />
                    <i
                      v-else
                      :class="[getConnectorIdentity(label)?.fallbackIcon, 'connector-brand-icon']"
                    />
                    <span class="connector-name">{{ label }}</span>
                  </div>
                  <span
                    v-else
                    class="inline-flex items-center rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-semibold text-slate-700 dark:border-slate-700 dark:bg-slate-950/60 dark:text-slate-200"
                  >
                    {{ label }}
                  </span>
                </template>
              </div>
            </div>

            <div v-if="displayBatchTotalModels > 0" class="grid grid-cols-3 gap-3 text-sm">
              <div class="rounded-xl border border-slate-200 bg-white px-3 py-2 text-center dark:border-slate-700 dark:bg-slate-950/60">
                <p class="text-xs uppercase tracking-wide text-slate-400 dark:text-slate-500">Total</p>
                <p class="mt-1 font-semibold text-slate-700 dark:text-slate-200">{{ displayBatchTotalModels }}</p>
              </div>
              <div class="rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-2 text-center dark:border-emerald-900/80 dark:bg-emerald-950/60">
                <p class="text-xs uppercase tracking-wide text-emerald-500 dark:text-emerald-300">Succeeded</p>
                <p class="mt-1 font-semibold text-emerald-700 dark:text-emerald-200">{{ batchSucceededCount }}</p>
              </div>
              <div class="rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-center dark:border-rose-900/80 dark:bg-rose-950/60">
                <p class="text-xs uppercase tracking-wide text-rose-500 dark:text-rose-300">Failed</p>
                <p class="mt-1 font-semibold text-rose-700 dark:text-rose-200">{{ batchFailedCount }}</p>
              </div>
            </div>
          </div>
        </div>

        <Message v-if="canStartBatchRun" severity="info" :closable="false">
          Review the selected {{ scopeLabel.toLowerCase() }} values and click Start to begin the batch run.
        </Message>

        <Message v-if="batchRunBusy" severity="warn" :closable="false">
          Running selected models sequentially and streaming live output. Shared upstream dependencies may be re-run for multiple target models.
        </Message>

        <Message v-else-if="batchRunError" severity="error" :closable="false">
          {{ batchRunError }}
        </Message>

        <Message v-else-if="batchRunResult && batchRunResult.totalModels === 0" severity="info" :closable="false">
          No models matched the selected {{ scopeLabel.toLowerCase() }} values.
        </Message>

        <div v-if="batchRunBusy || batchLiveOutput" class="rounded-2xl border border-slate-200 dark:border-slate-700">
          <pre ref="batchLiveOutputRef" class="max-h-64 overflow-auto bg-slate-950 px-4 py-3 text-xs leading-6 text-slate-100">{{ batchLiveOutput || 'Waiting for batch output...' }}</pre>
        </div>

        <DataTable
          v-else-if="batchRunItems.length"
          :value="batchRunItems"
          class="data-model-table"
          table-style="min-width: 100%"
          striped-rows
        >
          <Column header="Model Name">
            <template #body="{ data }">
              <span class="block max-w-[18rem] truncate font-mono text-[0.92rem] font-medium tracking-[0.01em] text-slate-700 dark:text-slate-200">
                {{ data.modelName }}
              </span>
            </template>
          </Column>

          <Column header="Scope">
            <template #body="{ data }">
              <div v-if="data.scopeLabel && scopeType === 'connector' && getConnectorIdentity(data.scopeLabel)" class="connector-identity">
                <img
                  v-if="getConnectorIdentity(data.scopeLabel)?.logo"
                  :src="getConnectorIdentity(data.scopeLabel)?.logo"
                  :alt="data.scopeLabel"
                  class="connector-brand-logo"
                />
                <i
                  v-else
                  :class="[getConnectorIdentity(data.scopeLabel)?.fallbackIcon, 'connector-brand-icon']"
                />
                <span class="connector-name">{{ data.scopeLabel }}</span>
              </div>
              <span
                v-else-if="data.scopeLabel"
                class="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold text-slate-600 dark:border-slate-700 dark:bg-slate-900/60 dark:text-slate-200"
              >
                {{ data.scopeLabel }}
              </span>
              <span v-else class="text-sm text-slate-500 dark:text-slate-400">—</span>
            </template>
          </Column>

          <Column header="Status">
            <template #body="{ data }">
              <span class="inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold" :class="resolveBatchItemStatusClass(data)">
                {{ resolveBatchItemStatusLabel(data) }}
              </span>
            </template>
          </Column>

          <Column header="Started At">
            <template #body="{ data }">
              <span class="text-sm text-slate-600 dark:text-slate-300">
                {{ data.startedAt || 'Not started' }}
              </span>
            </template>
          </Column>

          <Column header="Finished At">
            <template #body="{ data }">
              <span class="text-sm text-slate-600 dark:text-slate-300">
                {{ data.finishedAt || 'Not finished' }}
              </span>
            </template>
          </Column>

          <Column header="Output">
            <template #body="{ data }">
              <Button
                label="View Output"
                size="small"
                severity="secondary"
                outlined
                :disabled="!canViewBatchOutput(data)"
                @click="openBatchOutputDialog(data)"
              />
            </template>
          </Column>
        </DataTable>
      </div>

      <template #footer>
        <div class="flex w-full items-center justify-end gap-2">
          <Button label="Close" severity="secondary" outlined @click="batchRunDialogVisible = false" />
          <Button
            v-if="canStartBatchRun"
            label="Start"
            @click="startBatchRun"
          />
        </div>
      </template>
    </Dialog>

    <Dialog
      v-model:visible="batchOutputDialogVisible"
      modal
      :dismissable-mask="true"
      :close-on-escape="true"
      :header="selectedBatchOutputTitle"
      class="w-[min(92vw,56rem)]"
    >
      <div class="space-y-4" v-if="selectedBatchOutput">
        <div class="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-slate-50/80 p-4 dark:border-slate-700 dark:bg-slate-900/60">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div class="space-y-1">
              <p class="text-sm font-medium text-slate-700 dark:text-slate-200">Batch Run Result</p>
              <div class="flex flex-wrap items-center gap-2">
                <span
                  class="inline-flex items-center rounded-full border px-3 py-1 text-[0.7rem] font-semibold uppercase tracking-[0.08em]"
                  :class="resolveLayerClass(selectedBatchOutput.layer)"
                >
                  {{ selectedBatchOutput.layer }}
                </span>
                <div
                  v-if="selectedBatchOutput.scopeLabel && scopeType === 'connector' && getConnectorIdentity(selectedBatchOutput.scopeLabel)"
                  class="connector-identity rounded-full border border-slate-200 bg-white px-3 py-1 dark:border-slate-700 dark:bg-slate-950/60"
                >
                  <img
                    v-if="getConnectorIdentity(selectedBatchOutput.scopeLabel)?.logo"
                    :src="getConnectorIdentity(selectedBatchOutput.scopeLabel)?.logo"
                    :alt="selectedBatchOutput.scopeLabel"
                    class="connector-brand-logo"
                  />
                  <i
                    v-else
                    :class="[getConnectorIdentity(selectedBatchOutput.scopeLabel)?.fallbackIcon, 'connector-brand-icon']"
                  />
                  <span class="connector-name">{{ selectedBatchOutput.scopeLabel }}</span>
                </div>
                <span
                  v-else-if="selectedBatchOutput.scopeLabel"
                  class="inline-flex items-center rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-semibold text-slate-700 dark:border-slate-700 dark:bg-slate-950/60 dark:text-slate-200"
                >
                  {{ selectedBatchOutput.scopeLabel }}
                </span>
              </div>
            </div>
            <span class="inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold" :class="resolveBatchItemStatusClass(selectedBatchOutput)">
              {{ resolveBatchItemStatusLabel(selectedBatchOutput) }}
            </span>
          </div>

          <div class="grid gap-3 text-sm md:grid-cols-2">
            <div class="rounded-xl border border-slate-200 bg-white px-3 py-2 dark:border-slate-700 dark:bg-slate-950/60">
              <p class="text-xs uppercase tracking-wide text-slate-400 dark:text-slate-500">Started At</p>
              <p class="mt-1 font-medium text-slate-700 dark:text-slate-200">{{ selectedBatchOutput.startedAt || 'Not started' }}</p>
            </div>
            <div class="rounded-xl border border-slate-200 bg-white px-3 py-2 dark:border-slate-700 dark:bg-slate-950/60">
              <p class="text-xs uppercase tracking-wide text-slate-400 dark:text-slate-500">Finished At</p>
              <p class="mt-1 font-medium text-slate-700 dark:text-slate-200">{{ selectedBatchOutput.finishedAt || 'Not finished' }}</p>
            </div>
          </div>
        </div>

        <Message
          v-if="selectedBatchOutput.code || selectedBatchOutput.message"
          :severity="selectedBatchOutput.success ? 'info' : 'warn'"
          :closable="false"
        >
          {{ selectedBatchOutput.code || 'DBT_MODEL_RUN_RESULT' }}{{ selectedBatchOutput.message ? `: ${selectedBatchOutput.message}` : '' }}
        </Message>

        <div class="space-y-3">
          <div class="rounded-2xl border border-slate-200 dark:border-slate-700">
            <div class="border-b border-slate-200 px-4 py-3 dark:border-slate-700">
              <p class="text-sm font-semibold text-slate-700 dark:text-slate-200">stdout</p>
            </div>
            <pre class="max-h-64 overflow-auto bg-slate-950 px-4 py-3 text-xs leading-6 text-slate-100">{{ stripAnsi(selectedBatchOutput.stdout) || 'No stdout output.' }}</pre>
          </div>

          <div class="rounded-2xl border border-slate-200 dark:border-slate-700">
            <div class="border-b border-slate-200 px-4 py-3 dark:border-slate-700">
              <p class="text-sm font-semibold text-slate-700 dark:text-slate-200">stderr</p>
            </div>
            <pre class="max-h-64 overflow-auto bg-slate-950 px-4 py-3 text-xs leading-6 text-rose-200">{{ stripAnsi(selectedBatchOutput.stderr) || 'No stderr output.' }}</pre>
          </div>
        </div>
      </div>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { dbtModelApi } from '~/services/api/dbt';
import { getDbtLayerClass, stripAnsi } from '~/services/dbt/presentation';
import { useAuthStore } from '~/stores/auth';
import type { DbtRunHistoryDetail, DbtRunHistoryListItem, DbtRunModelHistoryItem } from '~/types/dbt';

type DataTablePageEvent = {
  page: number;
  rows: number;
  first: number;
};

type DataTableExpandEvent = {
  data: DbtRunHistoryListItem;
};

const DEFAULT_PAGE_SIZE = 10;

const auth = useAuthStore();
const { showToast } = useAppToast();

const searchQuery = ref('');
const debouncedSearchQuery = ref('');
const runHistoryItems = ref<DbtRunHistoryListItem[]>([]);
const expandedRows = ref<Record<string, boolean>>({});
const loading = ref(false);
const loadError = ref('');
const detailDialogVisible = ref(false);
const detailLoading = ref(false);
const detailError = ref('');
const selectedRun = ref<DbtRunHistoryListItem | null>(null);
const runDetail = ref<DbtRunHistoryDetail | null>(null);
const runModelHistoryCache = reactive<Record<string, DbtRunModelHistoryItem[]>>({});
const runModelHistoryLoading = reactive<Record<string, boolean>>({});
const runModelHistoryError = reactive<Record<string, string>>({});
const activeModelHistoryRequestIdByRun = reactive<Record<string, number>>({});
const pageState = reactive({
  page: 1,
  pageSize: DEFAULT_PAGE_SIZE,
  total: 0,
  totalPages: 0
});

let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null;
let activeListRequestId = 0;
let activeDetailRequestId = 0;

const selectedRunTitle = computed(() => {
  if (!selectedRun.value) {
    return 'Run Output';
  }

  return selectedRun.value.modelName;
});

const currentStatus = computed(() => runDetail.value?.status ?? selectedRun.value?.status ?? 'UNKNOWN');
const currentStatusLabel = computed(() => formatStatus(currentStatus.value));
const currentStatusClass = computed(() => resolveStatusClass(currentStatus.value));

const currentStartedAt = computed(() => runDetail.value?.startedAt ?? selectedRun.value?.startedAt ?? 'Not available');
const currentFinishedAt = computed(() => runDetail.value?.finishedAt ?? selectedRun.value?.finishedAt ?? 'Not available');
const currentReturnCode = computed(() => {
  const value = runDetail.value?.returncode ?? selectedRun.value?.returncode;
  return value == null ? 'N/A' : String(value);
});

const loadRuns = async () => {
  const requestId = ++activeListRequestId;
  loading.value = true;
  loadError.value = '';

  const result = await dbtModelApi.listRuns(auth.token, {
    page: pageState.page,
    pageSize: pageState.pageSize,
    search: debouncedSearchQuery.value || '%'
  });

  if (requestId !== activeListRequestId) {
    return;
  }

  loading.value = false;

  if (!result.success || !result.data) {
    runHistoryItems.value = [];
    expandedRows.value = {};
    pageState.total = 0;
    pageState.totalPages = 0;
    loadError.value = result.message ?? 'Unable to load dbt run logs.';
    return;
  }

  runHistoryItems.value = result.data.items;
  expandedRows.value = {};
  pageState.page = result.data.page.page;
  pageState.pageSize = result.data.page.pageSize;
  pageState.total = result.data.page.total;
  pageState.totalPages = result.data.page.totalPages;
};

const loadRunModels = async (runId: string) => {
  if (runModelHistoryCache[runId] || runModelHistoryLoading[runId]) {
    return;
  }

  const requestId = (activeModelHistoryRequestIdByRun[runId] ?? 0) + 1;
  activeModelHistoryRequestIdByRun[runId] = requestId;
  runModelHistoryLoading[runId] = true;
  runModelHistoryError[runId] = '';

  const result = await dbtModelApi.listRunModels(auth.token, runId);

  if (activeModelHistoryRequestIdByRun[runId] !== requestId) {
    return;
  }

  runModelHistoryLoading[runId] = false;

  if (!result.success || !result.data) {
    runModelHistoryCache[runId] = [];
    runModelHistoryError[runId] = result.message ?? 'Unable to load run model history.';
    return;
  }

  runModelHistoryCache[runId] = result.data.items;
};

const openDetailDialog = async (run: DbtRunHistoryListItem) => {
  const requestId = ++activeDetailRequestId;
  selectedRun.value = run;
  runDetail.value = null;
  detailError.value = '';
  detailDialogVisible.value = true;
  detailLoading.value = true;

  const result = await dbtModelApi.getRunDetail(auth.token, run.runId);

  if (requestId !== activeDetailRequestId) {
    return;
  }

  detailLoading.value = false;

  if (!result.success || !result.data) {
    detailError.value = result.message ?? 'Unable to load dbt run output.';
    showToast('error', detailError.value);
    return;
  }

  runDetail.value = result.data;
};

const copyRunId = async (runId: string) => {
  const fallbackCopy = () => {
    const textarea = document.createElement('textarea');
    textarea.value = runId;
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
      await navigator.clipboard.writeText(runId);
      showToast('success', 'Run ID copied.');
      return;
    }

    if (fallbackCopy()) {
      showToast('success', 'Run ID copied.');
      return;
    }
  } catch {
    if (fallbackCopy()) {
      showToast('success', 'Run ID copied.');
      return;
    }
  }

  showToast('error', 'Unable to copy Run ID.');
};

const onPage = (event: DataTablePageEvent) => {
  pageState.page = event.page + 1;
  pageState.pageSize = event.rows;
  loadRuns();
};

const onRowExpand = async (event: DataTableExpandEvent) => {
  const run = event.data;
  expandedRows.value = { [run.runId]: true };
  await loadRunModels(run.runId);
};

const onRowCollapse = () => {
  expandedRows.value = {};
};

const formatStatus = (value: string | null | undefined) => {
  const normalized = value?.trim();
  if (!normalized) {
    return 'Unknown';
  }

  return normalized
    .toLowerCase()
    .split('_')
    .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join(' ');
};

const resolveStatusClass = (value: string | null | undefined) => {
  const normalized = value?.trim().toUpperCase();
  if (normalized === 'SUCCESS') {
    return 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900/80 dark:bg-emerald-950/60 dark:text-emerald-200';
  }
  if (normalized === 'FAILED' || normalized === 'RUNNER_ERROR') {
    return 'border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-900/80 dark:bg-rose-950/60 dark:text-rose-200';
  }
  if (normalized === 'BUSY') {
    return 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900/80 dark:bg-amber-950/60 dark:text-amber-200';
  }

  return 'border-slate-200 bg-slate-50 text-slate-700 dark:border-slate-700 dark:bg-slate-900/60 dark:text-slate-200';
};

const resolveLayerClass = getDbtLayerClass;

const formatExecutionTime = (value: number | null | undefined) => {
  if (value == null || Number.isNaN(value)) {
    return 'N/A';
  }

  return `${value.toFixed(2)}s`;
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
  debouncedSearchQuery,
  () => {
    pageState.page = 1;
    loadRuns();
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  if (searchDebounceTimer) {
    clearTimeout(searchDebounceTimer);
  }
});
</script>

<template>
  <div class="app-panel space-y-5 p-5 sm:p-6">
    <div class="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
      <div class="min-w-0">
        <h2 class="text-2xl font-semibold text-slate-900 dark:text-slate-100">Run Logs</h2>
      </div>

      <IconField class="w-full lg:w-[22rem]">
        <InputIcon class="pi pi-search" />
        <InputText
          v-model.trim="searchQuery"
          type="search"
          class="w-full"
          placeholder="Search model, layer, or status"
          aria-label="Search model, layer, or status"
        />
      </IconField>
    </div>

    <Message v-if="loadError" severity="error" :closable="false">
      {{ loadError }}
    </Message>

    <DataTable
      v-model:expandedRows="expandedRows"
      :value="runHistoryItems"
      :loading="loading"
      :rows="pageState.pageSize"
      :first="(pageState.page - 1) * pageState.pageSize"
      :total-records="pageState.total"
      dataKey="runId"
      paginator
      lazy
      class="data-model-table"
      paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport"
      current-page-report-template="{first} - {last} of {totalRecords}"
      table-style="min-width: 100%"
      striped-rows
      @rowExpand="onRowExpand"
      @rowCollapse="onRowCollapse"
      @page="onPage"
    >
      <Column expander style="width: 3rem" />

      <Column header="Model Name">
        <template #body="{ data }">
          <span class="block max-w-[22rem] truncate font-mono text-[0.92rem] font-medium tracking-[0.01em] text-slate-700 dark:text-slate-200">
            {{ data.modelName }}
          </span>
        </template>
      </Column>

      <Column header="Layer">
        <template #body="{ data }">
          <span class="inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.08em]" :class="resolveLayerClass(data.layer)">
            {{ data.layer }}
          </span>
        </template>
      </Column>

      <Column header="Status">
        <template #body="{ data }">
          <span class="inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold" :class="resolveStatusClass(data.status)">
            {{ formatStatus(data.status) }}
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

      <Column header="Execution Time">
        <template #body="{ data }">
          <span class="font-mono text-sm text-slate-600 dark:text-slate-300">
            {{ formatExecutionTime(data.executionTimeSeconds) }}
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
            @click.stop="openDetailDialog(data)"
          />
        </template>
      </Column>

      <template #expansion="{ data }">
        <div class="rounded-2xl border border-slate-200 bg-slate-50/70 p-4 dark:border-slate-700 dark:bg-slate-900/50">
          <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
            <p class="text-sm font-semibold text-slate-700 dark:text-slate-200">Run Model History</p>
            <div class="connector-task-id-inline">
              <span class="connector-task-job-id">{{ data.runId }}</span>
              <button
                type="button"
                class="connector-task-copy-button"
                :aria-label="`Copy Run ID ${data.runId}`"
                @click.stop="copyRunId(data.runId)"
              >
                <i class="pi pi-copy" />
              </button>
            </div>
          </div>

          <Message v-if="runModelHistoryLoading[data.runId]" severity="info" :closable="false">
            Loading run model history.
          </Message>

          <Message v-else-if="runModelHistoryError[data.runId]" severity="error" :closable="false">
            {{ runModelHistoryError[data.runId] }}
          </Message>

          <div
            v-else-if="!(runModelHistoryCache[data.runId]?.length)"
            class="rounded-xl border border-dashed border-slate-300 bg-white/80 px-4 py-6 text-center text-sm text-slate-500 dark:border-slate-700 dark:bg-slate-950/50 dark:text-slate-400"
          >
            No model-level execution records are available for this run.
          </div>

          <DataTable
            v-else
            :value="runModelHistoryCache[data.runId]"
            size="small"
            table-style="min-width: 100%"
            class="data-model-table"
            striped-rows
          >
            <Column header="Model Name">
              <template #body="{ data: item }">
                <span class="block max-w-[22rem] truncate font-mono text-[0.88rem] font-medium tracking-[0.01em] text-slate-700 dark:text-slate-200">
                  {{ item.modelName }}
                </span>
              </template>
            </Column>

            <Column header="Layer">
              <template #body="{ data: item }">
                <span class="inline-flex items-center rounded-full border px-3 py-1 text-[0.7rem] font-semibold uppercase tracking-[0.08em]" :class="resolveLayerClass(item.layer)">
                  {{ item.layer }}
                </span>
              </template>
            </Column>

            <Column header="Status">
              <template #body="{ data: item }">
                <span class="inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold" :class="resolveStatusClass(item.status)">
                  {{ formatStatus(item.status) }}
                </span>
              </template>
            </Column>

            <Column header="Completed At">
              <template #body="{ data: item }">
                <span class="text-sm text-slate-600 dark:text-slate-300">
                  {{ item.completedAt || 'Not completed' }}
                </span>
              </template>
            </Column>

            <Column header="Execution Time">
              <template #body="{ data: item }">
                <span class="font-mono text-sm text-slate-600 dark:text-slate-300">
                  {{ formatExecutionTime(item.executionTimeSeconds) }}
                </span>
              </template>
            </Column>
          </DataTable>
        </div>
      </template>

      <template #empty>
        <div class="py-10 text-center text-sm text-slate-500 dark:text-slate-400">
          No dbt run logs found.
        </div>
      </template>
    </DataTable>

    <Dialog
      v-model:visible="detailDialogVisible"
      modal
      :dismissable-mask="true"
      :close-on-escape="true"
      :header="selectedRunTitle"
      class="w-[min(92vw,56rem)]"
    >
      <div class="space-y-4">
        <div class="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-slate-50/80 p-4 dark:border-slate-700 dark:bg-slate-900/60">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div class="space-y-1">
              <p class="text-sm font-medium text-slate-700 dark:text-slate-200">Run Status</p>
              <div class="flex flex-wrap items-center gap-2">
                <p class="text-xs text-slate-500 dark:text-slate-400">
                  {{ selectedRun?.modelName || 'No run selected' }}
                </p>
                <span
                  v-if="selectedRun?.layer"
                  class="inline-flex items-center rounded-full border px-3 py-1 text-[0.7rem] font-semibold uppercase tracking-[0.08em]"
                  :class="resolveLayerClass(selectedRun.layer)"
                >
                  {{ selectedRun.layer }}
                </span>
              </div>
            </div>
            <span class="inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold" :class="currentStatusClass">
              <i v-if="detailLoading" class="pi pi-spin pi-spinner" />
              <i v-else-if="currentStatus === 'SUCCESS'" class="pi pi-check-circle" />
              <i v-else-if="currentStatus === 'BUSY'" class="pi pi-clock" />
              <i v-else class="pi pi-times-circle" />
              {{ detailLoading ? 'Loading' : currentStatusLabel }}
            </span>
          </div>

          <div class="grid gap-3 text-sm md:grid-cols-2 xl:grid-cols-4">
            <div class="rounded-xl border border-slate-200 bg-white px-3 py-2 dark:border-slate-700 dark:bg-slate-950/60">
              <p class="text-xs uppercase tracking-wide text-slate-400 dark:text-slate-500">Layer</p>
              <span
                v-if="selectedRun?.layer"
                class="mt-2 inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.08em]"
                :class="resolveLayerClass(selectedRun.layer)"
              >
                {{ selectedRun.layer }}
              </span>
              <p v-else class="mt-1 font-medium text-slate-700 dark:text-slate-200">Unknown</p>
            </div>
            <div class="rounded-xl border border-slate-200 bg-white px-3 py-2 dark:border-slate-700 dark:bg-slate-950/60">
              <p class="text-xs uppercase tracking-wide text-slate-400 dark:text-slate-500">Started At</p>
              <p class="mt-1 font-medium text-slate-700 dark:text-slate-200">{{ currentStartedAt }}</p>
            </div>
            <div class="rounded-xl border border-slate-200 bg-white px-3 py-2 dark:border-slate-700 dark:bg-slate-950/60">
              <p class="text-xs uppercase tracking-wide text-slate-400 dark:text-slate-500">Finished At</p>
              <p class="mt-1 font-medium text-slate-700 dark:text-slate-200">{{ currentFinishedAt }}</p>
            </div>
            <div class="rounded-xl border border-slate-200 bg-white px-3 py-2 dark:border-slate-700 dark:bg-slate-950/60">
              <p class="text-xs uppercase tracking-wide text-slate-400 dark:text-slate-500">Return Code</p>
              <p class="mt-1 font-medium text-slate-700 dark:text-slate-200">{{ currentReturnCode }}</p>
            </div>
          </div>
        </div>

        <Message v-if="detailLoading" severity="info" :closable="false">
          Loading dbt run output.
        </Message>

        <Message v-else-if="detailError" severity="error" :closable="false">
          {{ detailError }}
        </Message>

        <Message
          v-else-if="runDetail?.errorCode || runDetail?.errorMessage"
          severity="warn"
          :closable="false"
        >
          {{ runDetail?.errorCode || 'DBT_RUN_ERROR' }}{{ runDetail?.errorMessage ? `: ${runDetail.errorMessage}` : '' }}
        </Message>

        <div class="space-y-3">
          <div class="rounded-2xl border border-slate-200 dark:border-slate-700">
            <div class="border-b border-slate-200 px-4 py-3 dark:border-slate-700">
              <p class="text-sm font-semibold text-slate-700 dark:text-slate-200">stdout</p>
            </div>
            <pre class="max-h-64 overflow-auto bg-slate-950 px-4 py-3 text-xs leading-6 text-slate-100">{{ stripAnsi(runDetail?.stdout) || 'No stdout output.' }}</pre>
          </div>

          <div class="rounded-2xl border border-slate-200 dark:border-slate-700">
            <div class="border-b border-slate-200 px-4 py-3 dark:border-slate-700">
              <p class="text-sm font-semibold text-slate-700 dark:text-slate-200">stderr</p>
            </div>
            <pre class="max-h-64 overflow-auto bg-slate-950 px-4 py-3 text-xs leading-6 text-rose-200">{{ stripAnsi(runDetail?.stderr) || 'No stderr output.' }}</pre>
          </div>
        </div>
      </div>
    </Dialog>
  </div>
</template>

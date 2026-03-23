<script setup lang="ts">
import { dbtModelApi } from '~/services/api/dbt';
import { useAuthStore } from '~/stores/auth';
import type { DbtRunHistoryDetail, DbtRunHistoryListItem } from '~/types/dbt';

type DataTablePageEvent = {
  page: number;
  rows: number;
  first: number;
};

const DEFAULT_PAGE_SIZE = 10;

const auth = useAuthStore();
const { showToast } = useAppToast();

const searchQuery = ref('');
const debouncedSearchQuery = ref('');
const runHistoryItems = ref<DbtRunHistoryListItem[]>([]);
const loading = ref(false);
const loadError = ref('');
const detailDialogVisible = ref(false);
const detailLoading = ref(false);
const detailError = ref('');
const selectedRun = ref<DbtRunHistoryListItem | null>(null);
const runDetail = ref<DbtRunHistoryDetail | null>(null);
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

  return `${selectedRun.value.modelName} · ${selectedRun.value.layer}`;
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
    pageState.total = 0;
    pageState.totalPages = 0;
    loadError.value = result.message ?? 'Unable to load dbt run logs.';
    return;
  }

  runHistoryItems.value = result.data.items;
  pageState.page = result.data.page.page;
  pageState.pageSize = result.data.page.pageSize;
  pageState.total = result.data.page.total;
  pageState.totalPages = result.data.page.totalPages;
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

const onPage = (event: DataTablePageEvent) => {
  pageState.page = event.page + 1;
  pageState.pageSize = event.rows;
  loadRuns();
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
      :value="runHistoryItems"
      :loading="loading"
      :rows="pageState.pageSize"
      :first="(pageState.page - 1) * pageState.pageSize"
      :total-records="pageState.total"
      paginator
      lazy
      class="data-model-table"
      paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport"
      current-page-report-template="{first} - {last} of {totalRecords}"
      table-style="min-width: 100%"
      striped-rows
      @page="onPage"
    >
      <Column header="Model Name">
        <template #body="{ data }">
          <div class="max-w-[22rem]">
            <span
              class="inline-flex max-w-full items-center gap-2 rounded-lg border border-slate-200 bg-slate-50/85 px-2.5 py-1.5 font-mono text-[0.88rem] font-medium text-slate-700 dark:border-slate-700 dark:bg-slate-900/55 dark:text-slate-200"
            >
              <span
                aria-hidden="true"
                class="h-2.5 w-2.5 shrink-0 rotate-45 rounded-[2px] border border-slate-400/70 bg-white dark:border-slate-500 dark:bg-slate-800"
              />
              <span class="truncate whitespace-nowrap tracking-[0.01em]">{{ data.modelName }}</span>
            </span>
          </div>
        </template>
      </Column>

      <Column header="Layer">
        <template #body="{ data }">
          <span class="text-sm font-medium text-slate-600 uppercase tracking-[0.08em] dark:text-slate-300">
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

      <Column header="Return Code">
        <template #body="{ data }">
          <span class="font-mono text-sm text-slate-600 dark:text-slate-300">
            {{ data.returncode == null ? 'N/A' : data.returncode }}
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
            @click="openDetailDialog(data)"
          />
        </template>
      </Column>

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
              <p class="text-xs text-slate-500 dark:text-slate-400">
                {{ selectedRun?.modelName || 'No run selected' }}
              </p>
            </div>
            <span class="inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold" :class="currentStatusClass">
              <i v-if="detailLoading" class="pi pi-spin pi-spinner" />
              <i v-else-if="currentStatus === 'SUCCESS'" class="pi pi-check-circle" />
              <i v-else-if="currentStatus === 'BUSY'" class="pi pi-clock" />
              <i v-else class="pi pi-times-circle" />
              {{ detailLoading ? 'Loading' : currentStatusLabel }}
            </span>
          </div>

          <div class="grid gap-3 text-sm md:grid-cols-3">
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
            <pre class="max-h-64 overflow-auto bg-slate-950 px-4 py-3 text-xs leading-6 text-slate-100">{{ runDetail?.stdout || 'No stdout output.' }}</pre>
          </div>

          <div class="rounded-2xl border border-slate-200 dark:border-slate-700">
            <div class="border-b border-slate-200 px-4 py-3 dark:border-slate-700">
              <p class="text-sm font-semibold text-slate-700 dark:text-slate-200">stderr</p>
            </div>
            <pre class="max-h-64 overflow-auto bg-slate-950 px-4 py-3 text-xs leading-6 text-rose-200">{{ runDetail?.stderr || 'No stderr output.' }}</pre>
          </div>
        </div>
      </div>
    </Dialog>
  </div>
</template>

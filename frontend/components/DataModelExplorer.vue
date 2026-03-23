<script setup lang="ts">
import { dbtModelApi } from '~/services/api/dbt';
import { useAuthStore } from '~/stores/auth';
import type { DbtModelLayer, DbtModelListItem, RunDbtModelResult } from '~/types/dbt';

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
const loading = ref(false);
const loadError = ref('');
const runDialogVisible = ref(false);
const selectedModel = ref<DbtModelListItem | null>(null);
const runningModelName = ref('');
const runBusy = ref(false);
const runErrorMessage = ref('');
const runResult = ref<RunDbtModelResult | null>(null);

let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null;
let activeLoadRequestId = 0;

const layerMeta = computed(() => layerMetaMap[props.layer]);
const selectedModelTitle = computed(() => {
  if (!selectedModel.value) {
    return 'Run dbt Model';
  }

  return `${selectedModel.value.name} · ${selectedModel.value.layer}`;
});
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

const openRunDialog = async (model: DbtModelListItem) => {
  selectedModel.value = model;
  runningModelName.value = model.name;
  runDialogVisible.value = true;
  runBusy.value = true;
  runErrorMessage.value = '';
  runResult.value = null;

  const result = await dbtModelApi.run(auth.token, {
    layer: model.layer,
    modelName: model.name
  });

  if (result.data) {
    runResult.value = result.data;
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
    loadModels();
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
        <h2 class="text-2xl font-semibold text-slate-900 dark:text-slate-100">{{ layerMeta.title }}</h2>
      </div>

      <IconField class="w-full lg:w-[18rem]">
        <InputIcon class="pi pi-search" />
        <InputText
          v-model.trim="searchQuery"
          type="search"
          class="w-full"
          placeholder="Search model name"
          aria-label="Search model name"
        />
      </IconField>
    </div>

    <Message v-if="loadError" severity="error" :closable="false">
      {{ loadError }}
    </Message>

    <DataTable
      :value="models"
      :loading="loading"
      class="data-model-table"
      table-style="min-width: 100%"
      striped-rows
    >
      <Column header="Model Name">
        <template #body="{ data }">
          <div class="max-w-[30rem]">
            <span
              class="inline-flex max-w-full items-center gap-2 rounded-lg border border-slate-200 bg-slate-50/85 px-2.5 py-1.5 font-mono text-[0.9rem] font-medium text-slate-700 dark:border-slate-700 dark:bg-slate-900/55 dark:text-slate-200"
            >
              <span
                aria-hidden="true"
                class="h-2.5 w-2.5 shrink-0 rotate-45 rounded-[2px] border border-slate-400/70 bg-white dark:border-slate-500 dark:bg-slate-800"
              />
              <span class="truncate whitespace-nowrap tracking-[0.01em]">{{ data.name }}</span>
            </span>
          </div>
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
            :disabled="runBusy"
            @click="openRunDialog(data)"
          />
        </template>
      </Column>

      <template #empty>
        <div class="py-10 text-center text-sm text-slate-500 dark:text-slate-400">
          {{ layerMeta.emptyText }}
        </div>
      </template>
    </DataTable>

    <Dialog
      v-model:visible="runDialogVisible"
      modal
      :dismissable-mask="true"
      :close-on-escape="true"
      :header="selectedModelTitle"
      class="w-[min(92vw,56rem)]"
    >
      <div class="space-y-4">
        <div class="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-slate-50/80 p-4 dark:border-slate-700 dark:bg-slate-900/60">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div class="space-y-1">
              <p class="text-sm font-medium text-slate-700 dark:text-slate-200">Current Run Status</p>
              <p class="text-xs text-slate-500 dark:text-slate-400">
                {{ selectedModel?.name || 'No model selected' }}
              </p>
            </div>
            <span class="inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold" :class="runStatusClass">
              <i v-if="runBusy" class="pi pi-spin pi-spinner" />
              <i v-else-if="runResult?.success" class="pi pi-check-circle" />
              <i v-else-if="runResult" class="pi pi-times-circle" />
              {{ runStatusLabel }}
            </span>
          </div>

          <div class="grid gap-3 text-sm md:grid-cols-2">
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

        <Message v-if="runBusy" severity="warn" :closable="false">
          dbt is currently running this model. You can close the dialog, but the backend run will continue.
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

        <div class="space-y-3">
          <div class="rounded-2xl border border-slate-200 dark:border-slate-700">
            <div class="border-b border-slate-200 px-4 py-3 dark:border-slate-700">
              <p class="text-sm font-semibold text-slate-700 dark:text-slate-200">stdout</p>
            </div>
            <pre class="max-h-64 overflow-auto bg-slate-950 px-4 py-3 text-xs leading-6 text-slate-100">{{ runResult?.stdout || 'No stdout output.' }}</pre>
          </div>

          <div class="rounded-2xl border border-slate-200 dark:border-slate-700">
            <div class="border-b border-slate-200 px-4 py-3 dark:border-slate-700">
              <p class="text-sm font-semibold text-slate-700 dark:text-slate-200">stderr</p>
            </div>
            <pre class="max-h-64 overflow-auto bg-slate-950 px-4 py-3 text-xs leading-6 text-rose-200">{{ runResult?.stderr || 'No stderr output.' }}</pre>
          </div>
        </div>
      </div>
    </Dialog>
  </div>
</template>

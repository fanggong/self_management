export const getDbtLayerClass = (value: string | null | undefined) => {
  const normalized = value?.trim().toLowerCase();
  if (normalized === 'staging') {
    return 'border-sky-200 bg-sky-50 text-sky-700 dark:border-sky-900/80 dark:bg-sky-950/60 dark:text-sky-200';
  }
  if (normalized === 'intermediate') {
    return 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900/80 dark:bg-amber-950/60 dark:text-amber-200';
  }
  if (normalized === 'marts') {
    return 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900/80 dark:bg-emerald-950/60 dark:text-emerald-200';
  }

  return 'border-slate-200 bg-slate-50 text-slate-700 dark:border-slate-700 dark:bg-slate-900/60 dark:text-slate-200';
};

const ANSI_ESCAPE_PATTERN = /\u001b\[[0-?]*[ -/]*[@-~]/g;

export const stripAnsi = (value: string | null | undefined) => {
  if (!value) {
    return '';
  }

  return value.replace(ANSI_ESCAPE_PATTERN, '');
};

export type DbtModelLayer = 'staging' | 'intermediate' | 'marts';

export type DbtModelListItem = {
  name: string;
  layer: DbtModelLayer;
  lastRunCompletedAt: string | null;
};

export type DbtModelListResponse = {
  items: DbtModelListItem[];
};

export type ListDbtModelsPayload = {
  layer: DbtModelLayer;
  search?: string;
};

export type RunDbtModelPayload = {
  layer: DbtModelLayer;
  modelName: string;
};

export type RunDbtModelResult = {
  success: boolean;
  returncode: number;
  stdout: string;
  stderr: string;
  startedAt?: string | null;
  finishedAt?: string | null;
};

export type DbtRunHistoryListItem = {
  runId: string;
  modelName: string;
  layer: DbtModelLayer;
  status: string;
  returncode: number | null;
  startedAt: string | null;
  finishedAt: string | null;
};

export type DbtRunHistoryPage = {
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
};

export type DbtRunHistoryListResponse = {
  items: DbtRunHistoryListItem[];
  page: DbtRunHistoryPage;
};

export type ListDbtRunHistoryPayload = {
  page?: number;
  pageSize?: number;
  search?: string;
};

export type DbtRunHistoryDetail = {
  runId: string;
  modelName: string;
  layer: DbtModelLayer;
  status: string;
  returncode: number | null;
  startedAt: string | null;
  finishedAt: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  stdout: string;
  stderr: string;
};

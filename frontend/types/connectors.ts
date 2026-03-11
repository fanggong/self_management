export type ConnectorCategory = 'health' | 'finance';

export type ConnectorId = 'garmin-connect';

export type ConnectorStatus = 'not_configured' | 'running' | 'stopped';

export type ConnectorFieldType = 'text' | 'password';

export type ConnectorFieldSchema = {
  key: string;
  label: string;
  type: ConnectorFieldType;
  placeholder: string;
  required?: boolean;
  autocomplete?: string;
};

export type ConnectorDefinition = {
  id: ConnectorId;
  name: string;
  category: ConnectorCategory;
  fields: ConnectorFieldSchema[];
};

export type ConnectorConfigValues = Record<string, string>;

export type ConnectorRecord = {
  id: ConnectorId;
  name: string;
  category: ConnectorCategory;
  status: ConnectorStatus;
  schedule: string;
  lastRun: string;
  nextRun: string;
  config: ConnectorConfigValues;
};

export type TestConnectorPayload = {
  id: ConnectorId;
  config: ConnectorConfigValues;
};

export type SaveConnectorPayload = {
  id: ConnectorId;
  schedule: string;
  config: ConnectorConfigValues;
};

export type UpdateConnectorStatusPayload = {
  id: ConnectorId;
  status: Exclude<ConnectorStatus, 'not_configured'>;
};

export type CreateSyncJobPayload = {
  id: ConnectorId;
  startAt: string;
  endAt: string;
};

export type SyncJobStatus = 'queued' | 'running' | 'success' | 'failed';

export type SyncJobTriggerType = 'manual' | 'scheduled';

export type SyncJobDomain = 'health' | 'finance';

export type SyncJobPeriod = 'yesterday' | 'last_7_days' | 'last_30_days';

export type SyncJobRecord = {
  jobId: string;
  connectorId: ConnectorId;
  status: SyncJobStatus;
  triggerType: SyncJobTriggerType;
  startAt: string;
  endAt: string;
  startedAt: string;
  finishedAt: string;
  fetchedCount: number;
  insertedCount: number;
  updatedCount: number;
  unchangedCount: number;
  dedupedCount: number;
  errorMessage?: string;
  createdAt: string;
};

export type SyncJobListItem = {
  jobId: string;
  connectorId: string;
  connectorName: string;
  domain: SyncJobDomain;
  status: SyncJobStatus;
  triggerType: SyncJobTriggerType;
  windowStart: string | null;
  windowEnd: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  fetchedCount: number;
  insertedCount: number;
  updatedCount: number;
  dedupedCount: number;
  errorMessage: string | null;
  createdAt: string;
};

export type SyncJobListFacets = {
  allTasks: number;
  status: {
    queued: number;
    running: number;
    success: number;
    failed: number;
  };
  triggerType: {
    manual: number;
    scheduled: number;
  };
  domain: {
    health: number;
    finance: number;
  };
  period: {
    yesterday: number;
    last7Days: number;
    last30Days: number;
  };
};

export type SyncJobListResponse = {
  items: SyncJobListItem[];
  page: {
    page: number;
    pageSize: number;
    total: number;
    totalPages: number;
  };
  facets: SyncJobListFacets;
};

export type ListSyncJobsPayload = {
  page?: number;
  pageSize?: number;
  search?: string;
  period?: SyncJobPeriod;
  status?: SyncJobStatus;
  triggerType?: SyncJobTriggerType;
  domain?: SyncJobDomain;
  sortBy?: 'createdAt' | 'windowStart' | 'windowEnd' | 'startedAt' | 'finishedAt';
  sortOrder?: 'asc' | 'desc';
};

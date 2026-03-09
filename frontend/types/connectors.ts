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

export type SyncJobRecord = {
  jobId: string;
  connectorId: ConnectorId;
  status: 'queued' | 'running' | 'success' | 'failed';
  triggerType: 'scheduled' | 'manual' | 'retry' | 'backfill';
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

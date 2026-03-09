import type { ApiResult } from '~/types/auth';
import type {
  ConnectorConfigValues,
  ConnectorDefinition,
  ConnectorRecord,
  SaveConnectorPayload,
  TestConnectorPayload,
  UpdateConnectorStatusPayload
} from '~/types/connectors';
import { formatDateTime, getNextRunFromCron } from '~/services/connectors/cron';
import { connectorCatalog, getConnectorDefinition } from '~/services/connectors/catalog';

const CONNECTORS_KEY = 'sm_connectors';

const createInitialConnector = (definition: ConnectorDefinition): ConnectorRecord => {
  if (definition.id === 'garmin-connect') {
    return {
      id: definition.id,
      name: definition.name,
      category: definition.category,
      status: 'not_configured',
      schedule: '0 2 * * *',
      lastRun: '2026-03-05 02:00:00',
      nextRun: '2026-03-06 02:00:00',
      config: {
        username: '',
        password: ''
      }
    };
  }

  return {
    id: definition.id,
    name: definition.name,
    category: definition.category,
    status: 'not_configured',
    schedule: '0 2 * * *',
    lastRun: '',
    nextRun: '',
    config: {}
  };
};

const INITIAL_CONNECTORS: ConnectorRecord[] = connectorCatalog.map((definition) => createInitialConnector(definition));
const serverConnectors: ConnectorRecord[] = INITIAL_CONNECTORS.map((connector) => ({
  ...connector,
  config: { ...connector.config }
}));

const wait = (ms = 350) => new Promise((resolve) => setTimeout(resolve, ms));

const cloneConnector = (connector: ConnectorRecord): ConnectorRecord => {
  return {
    ...connector,
    config: { ...connector.config }
  };
};

const normalizeConnectorConfig = (
  definition: ConnectorDefinition | undefined,
  config: ConnectorConfigValues
): ConnectorConfigValues => {
  if (!definition) {
    return { ...config };
  }

  const normalized: ConnectorConfigValues = {};
  for (const field of definition.fields) {
    normalized[field.key] = String(config[field.key] ?? '');
  }

  return normalized;
};

const hasValidConfiguration = (
  definition: ConnectorDefinition | undefined,
  config: ConnectorConfigValues
): boolean => {
  if (!definition) {
    return false;
  }

  return !definition.fields.some((field) => field.required && !String(config[field.key] ?? '').trim());
};

const normalizeConnectorStatus = (
  definition: ConnectorDefinition | undefined,
  status: ConnectorRecord['status'] | undefined,
  config: ConnectorConfigValues
): ConnectorRecord['status'] => {
  if (!hasValidConfiguration(definition, config)) {
    return 'not_configured';
  }

  if (status === 'running' || status === 'stopped') {
    return status;
  }

  return 'stopped';
};

const parseConnectors = (raw: string | null): ConnectorRecord[] => {
  if (!raw) {
    return INITIAL_CONNECTORS.map((connector) => cloneConnector(connector));
  }

  try {
    const parsed = JSON.parse(raw) as ConnectorRecord[];
    if (!Array.isArray(parsed) || parsed.length === 0) {
      return INITIAL_CONNECTORS.map((connector) => cloneConnector(connector));
    }

    return parsed.map((connector) => {
      const definition = getConnectorDefinition(connector.id);
      return {
        ...connector,
        name: definition?.name ?? connector.name,
        category: definition?.category ?? connector.category,
        config: normalizeConnectorConfig(definition, connector.config ?? {}),
        status: normalizeConnectorStatus(definition, connector.status, normalizeConnectorConfig(definition, connector.config ?? {}))
      };
    });
  } catch {
    return INITIAL_CONNECTORS.map((connector) => cloneConnector(connector));
  }
};

const readConnectors = (): ConnectorRecord[] => {
  if (!import.meta.client) {
    return serverConnectors.map((connector) => cloneConnector(connector));
  }

  return parseConnectors(localStorage.getItem(CONNECTORS_KEY));
};

const writeConnectors = (connectors: ConnectorRecord[]) => {
  if (!import.meta.client) {
    serverConnectors.splice(0, serverConnectors.length, ...connectors.map((connector) => cloneConnector(connector)));
    return;
  }

  localStorage.setItem(CONNECTORS_KEY, JSON.stringify(connectors));
};

const validateConnectorConfig = (
  definition: ConnectorDefinition | undefined,
  config: ConnectorConfigValues
): string | null => {
  if (!definition) {
    return 'Connector definition not found.';
  }

  for (const field of definition.fields) {
    if (field.required && !String(config[field.key] ?? '').trim()) {
      return `${field.label} is required.`;
    }
  }

  if (definition.id === 'garmin-connect') {
    const password = String(config.password ?? '');
    if (password.length < 6) {
      return 'Password must be at least 6 characters.';
    }
  }

  return null;
};

export const mockConnectorApi = {
  async list(): Promise<ApiResult<ConnectorRecord[]>> {
    await wait(120);

    return {
      success: true,
      data: readConnectors()
    };
  },

  async testConnection(payload: TestConnectorPayload): Promise<ApiResult<null>> {
    await wait(500);

    const definition = getConnectorDefinition(payload.id);
    const normalizedConfig = normalizeConnectorConfig(definition, payload.config);
    const validationError = validateConnectorConfig(definition, normalizedConfig);

    if (validationError) {
      return {
        success: false,
        message: validationError,
        code: 'CONNECTOR_VALIDATION_ERROR'
      };
    }

    return {
      success: true,
      data: null,
      message: `${definition?.name ?? 'Connector'} connection verified successfully.`
    };
  },

  async saveConfiguration(payload: SaveConnectorPayload): Promise<ApiResult<ConnectorRecord>> {
    await wait(300);

    const definition = getConnectorDefinition(payload.id);
    const normalizedConfig = normalizeConnectorConfig(definition, payload.config);
    const validationError = validateConnectorConfig(definition, normalizedConfig);

    if (validationError) {
      return {
        success: false,
        message: validationError,
        code: 'CONNECTOR_VALIDATION_ERROR'
      };
    }

    const nextRunResult = getNextRunFromCron(payload.schedule);
    if (!nextRunResult.success || !nextRunResult.nextRun) {
      return {
        success: false,
        message: nextRunResult.message ?? 'Invalid cron expression.',
        code: 'INVALID_CRON'
      };
    }

    const connectors = readConnectors();
    const connectorIndex = connectors.findIndex((connector) => connector.id === payload.id);

    if (connectorIndex === -1) {
      return {
        success: false,
        message: 'Connector not found.',
        code: 'CONNECTOR_NOT_FOUND'
      };
    }

    const updatedConnector: ConnectorRecord = {
      ...connectors[connectorIndex],
      schedule: payload.schedule.trim(),
      nextRun: formatDateTime(nextRunResult.nextRun),
      config: normalizedConfig,
      status: normalizeConnectorStatus(definition, connectors[connectorIndex].status, normalizedConfig)
    };

    connectors.splice(connectorIndex, 1, updatedConnector);
    writeConnectors(connectors);

    return {
      success: true,
      data: cloneConnector(updatedConnector),
      message: `${updatedConnector.name} configuration saved.`
    };
  },

  async updateStatus(payload: UpdateConnectorStatusPayload): Promise<ApiResult<ConnectorRecord>> {
    await wait(180);

    const connectors = readConnectors();
    const connectorIndex = connectors.findIndex((connector) => connector.id === payload.id);

    if (connectorIndex === -1) {
      return {
        success: false,
        message: 'Connector not found.',
        code: 'CONNECTOR_NOT_FOUND'
      };
    }

    const connector = connectors[connectorIndex];
    const definition = getConnectorDefinition(connector.id);

    if (!hasValidConfiguration(definition, connector.config)) {
      return {
        success: false,
        message: 'Configure this connector before changing its status.',
        code: 'CONNECTOR_NOT_CONFIGURED'
      };
    }

    const updatedConnector: ConnectorRecord = {
      ...connector,
      status: payload.status
    };

    connectors.splice(connectorIndex, 1, updatedConnector);
    writeConnectors(connectors);

    return {
      success: true,
      data: cloneConnector(updatedConnector),
      message: `${updatedConnector.name} is now ${updatedConnector.status === 'running' ? 'running' : 'stopped'}.`
    };
  }
};

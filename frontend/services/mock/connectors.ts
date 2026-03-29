import type { ApiResult } from '~/types/auth';
import type {
  ConnectorConfigValues,
  ConnectorDefinition,
  ConnectorRecord,
  ConnectorSecretFieldState,
  SaveConnectorPayload,
  TestConnectorPayload,
  UpdateConnectorStatusPayload
} from '~/types/connectors';
import { formatDateTime, getNextRunFromCron } from '~/services/connectors/cron';
import { connectorCatalog, getConnectorDefinition } from '~/services/connectors/catalog';

const CONNECTORS_KEY = 'sm_connectors';
const SAFE_CONFIG_FIELD_KEYS: Record<ConnectorRecord['id'], string[]> = {
  'garmin-connect': ['username'],
  'medical-report': ['provider', 'modelId']
};
const SECRET_FIELD_KEYS: Record<ConnectorRecord['id'], string[]> = {
  'garmin-connect': ['password'],
  'medical-report': ['apiKey']
};

type StoredConnectorRecord = ConnectorRecord & {
  secrets: ConnectorConfigValues;
};

const wait = (ms = 350) => new Promise((resolve) => setTimeout(resolve, ms));

const getSafeFieldKeys = (definition: ConnectorDefinition | undefined) => {
  return definition ? SAFE_CONFIG_FIELD_KEYS[definition.id] : [];
};

const getSecretFieldKeys = (definition: ConnectorDefinition | undefined) => {
  return definition ? SECRET_FIELD_KEYS[definition.id] : [];
};

const pickConfigValues = (keys: string[], source: Record<string, unknown> | null | undefined): ConnectorConfigValues => {
  return Object.fromEntries(keys.map((key) => [key, String(source?.[key] ?? '')]));
};

const normalizePublicConnectorConfig = (
  definition: ConnectorDefinition | undefined,
  config: Record<string, unknown> | null | undefined
): ConnectorConfigValues => {
  if (!definition) {
    return {};
  }

  return pickConfigValues(getSafeFieldKeys(definition), config);
};

const normalizeSecretConfig = (
  definition: ConnectorDefinition | undefined,
  config: Record<string, unknown> | null | undefined
): ConnectorConfigValues => {
  if (!definition) {
    return {};
  }

  return pickConfigValues(getSecretFieldKeys(definition), config);
};

const buildSecretFieldState = (
  definition: ConnectorDefinition | undefined,
  secrets: ConnectorConfigValues
): ConnectorSecretFieldState => {
  if (!definition) {
    return {};
  }

  return Object.fromEntries(
    getSecretFieldKeys(definition).map((key) => [key, Boolean(String(secrets[key] ?? '').trim())])
  );
};

const toBooleanFlag = (value: unknown) => {
  if (typeof value === 'string') {
    return value.trim().toLowerCase() === 'true';
  }

  return Boolean(value);
};

const normalizeSecretFieldState = (
  definition: ConnectorDefinition | undefined,
  rawState: Record<string, unknown> | null | undefined,
  secrets: ConnectorConfigValues
): ConnectorSecretFieldState => {
  if (!definition) {
    return {};
  }

  const derivedState = buildSecretFieldState(definition, secrets);
  return Object.fromEntries(
    getSecretFieldKeys(definition).map((key) => [
      key,
      rawState?.[key] == null ? derivedState[key] : toBooleanFlag(rawState[key])
    ])
  );
};

const createInitialConnector = (definition: ConnectorDefinition): StoredConnectorRecord => {
  if (definition.id === 'garmin-connect') {
    const secrets = { password: '' };
    return {
      id: definition.id,
      name: definition.name,
      category: definition.category,
      status: 'not_configured',
      schedule: '0 2 * * *',
      lastRun: '2026-03-05 02:00:00',
      nextRun: '2026-03-06 02:00:00',
      config: {
        username: ''
      },
      secretFieldsConfigured: buildSecretFieldState(definition, secrets),
      secrets
    };
  }

  if (definition.id === 'medical-report') {
    const secrets = { apiKey: '' };
    return {
      id: definition.id,
      name: definition.name,
      category: definition.category,
      status: 'not_configured',
      schedule: '-',
      lastRun: '2026-03-10 09:30:00',
      nextRun: '-',
      config: {
        provider: '',
        modelId: ''
      },
      secretFieldsConfigured: buildSecretFieldState(definition, secrets),
      secrets
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
    config: {},
    secretFieldsConfigured: {},
    secrets: {}
  };
};

const INITIAL_CONNECTORS: StoredConnectorRecord[] = connectorCatalog.map((definition) => createInitialConnector(definition));
const serverConnectors: StoredConnectorRecord[] = INITIAL_CONNECTORS.map((connector) => ({
  ...connector,
  config: { ...connector.config },
  secretFieldsConfigured: { ...connector.secretFieldsConfigured },
  secrets: { ...connector.secrets }
}));

const cloneConnector = (connector: StoredConnectorRecord): ConnectorRecord => {
  return {
    id: connector.id,
    name: connector.name,
    category: connector.category,
    status: connector.status,
    schedule: connector.schedule,
    lastRun: connector.lastRun,
    nextRun: connector.nextRun,
    config: { ...connector.config },
    secretFieldsConfigured: { ...connector.secretFieldsConfigured }
  };
};

const cloneStoredConnector = (connector: StoredConnectorRecord): StoredConnectorRecord => {
  return {
    ...connector,
    config: { ...connector.config },
    secretFieldsConfigured: { ...connector.secretFieldsConfigured },
    secrets: { ...connector.secrets }
  };
};

const mergeSecretConfig = (
  definition: ConnectorDefinition | undefined,
  requestSecrets: ConnectorConfigValues,
  storedSecrets: ConnectorConfigValues
): ConnectorConfigValues => {
  if (!definition) {
    return {};
  }

  return Object.fromEntries(
    getSecretFieldKeys(definition).map((key) => {
      const requestValue = String(requestSecrets[key] ?? '');
      return [key, requestValue.trim() ? requestValue : String(storedSecrets[key] ?? '')];
    })
  );
};

const buildCompleteConfig = (
  definition: ConnectorDefinition | undefined,
  config: ConnectorConfigValues,
  secrets: ConnectorConfigValues
): ConnectorConfigValues => {
  if (!definition) {
    return {};
  }

  return {
    ...pickConfigValues(getSafeFieldKeys(definition), config),
    ...pickConfigValues(getSecretFieldKeys(definition), secrets)
  };
};

const hasValidConfiguration = (
  definition: ConnectorDefinition | undefined,
  config: ConnectorConfigValues,
  secrets: ConnectorConfigValues
): boolean => {
  if (!definition) {
    return false;
  }

  const completeConfig = buildCompleteConfig(definition, config, secrets);
  return !definition.fields.some((field) => field.required && !String(completeConfig[field.key] ?? '').trim());
};

const normalizeConnectorStatus = (
  definition: ConnectorDefinition | undefined,
  status: ConnectorRecord['status'] | undefined,
  config: ConnectorConfigValues,
  secrets: ConnectorConfigValues
): ConnectorRecord['status'] => {
  if (!hasValidConfiguration(definition, config, secrets)) {
    return 'not_configured';
  }

  if (status === 'running' || status === 'stopped') {
    return status;
  }

  return 'stopped';
};

const toStoredConnector = (definition: ConnectorDefinition, rawConnector: Record<string, unknown> | null | undefined): StoredConnectorRecord => {
  const initialConnector = createInitialConnector(definition);
  const rawConfig = (rawConnector?.config as Record<string, unknown> | undefined) ?? {};
  const rawSecrets = (rawConnector?.secrets as Record<string, unknown> | undefined) ?? {};
  const legacySecrets = normalizeSecretConfig(definition, rawConfig);
  const normalizedSecrets = normalizeSecretConfig(definition, {
    ...legacySecrets,
    ...rawSecrets
  });
  const normalizedConfig = normalizePublicConnectorConfig(definition, rawConfig);

  return {
    id: definition.id,
    name: definition.name,
    category: definition.category,
    status: normalizeConnectorStatus(
      definition,
      (rawConnector?.status as ConnectorRecord['status'] | undefined) ?? initialConnector.status,
      normalizedConfig,
      normalizedSecrets
    ),
    schedule: String(rawConnector?.schedule ?? initialConnector.schedule),
    lastRun: String(rawConnector?.lastRun ?? initialConnector.lastRun),
    nextRun: String(rawConnector?.nextRun ?? initialConnector.nextRun),
    config: normalizedConfig,
    secretFieldsConfigured: normalizeSecretFieldState(
      definition,
      rawConnector?.secretFieldsConfigured as Record<string, unknown> | undefined,
      normalizedSecrets
    ),
    secrets: normalizedSecrets
  };
};

const parseConnectors = (raw: string | null): StoredConnectorRecord[] => {
  if (!raw) {
    return INITIAL_CONNECTORS.map((connector) => cloneStoredConnector(connector));
  }

  try {
    const parsed = JSON.parse(raw) as Array<Record<string, unknown>>;
    if (!Array.isArray(parsed) || parsed.length === 0) {
      return INITIAL_CONNECTORS.map((connector) => cloneStoredConnector(connector));
    }

    const parsedById = new Map(parsed.map((connector) => [String(connector.id ?? ''), connector]));
    return connectorCatalog.map((definition) => toStoredConnector(definition, parsedById.get(definition.id)));
  } catch {
    return INITIAL_CONNECTORS.map((connector) => cloneStoredConnector(connector));
  }
};

const readConnectors = (): StoredConnectorRecord[] => {
  if (!import.meta.client) {
    return serverConnectors.map((connector) => cloneStoredConnector(connector));
  }

  return parseConnectors(localStorage.getItem(CONNECTORS_KEY));
};

const writeConnectors = (connectors: StoredConnectorRecord[]) => {
  if (!import.meta.client) {
    serverConnectors.splice(0, serverConnectors.length, ...connectors.map((connector) => cloneStoredConnector(connector)));
    return;
  }

  localStorage.setItem(CONNECTORS_KEY, JSON.stringify(connectors));
};

const validateConnectorConfig = (
  definition: ConnectorDefinition | undefined,
  config: ConnectorConfigValues,
  secrets: ConnectorConfigValues
): string | null => {
  if (!definition) {
    return 'Connector definition not found.';
  }

  const completeConfig = buildCompleteConfig(definition, config, secrets);
  for (const field of definition.fields) {
    if (field.required && !String(completeConfig[field.key] ?? '').trim()) {
      return `${field.label} is required.`;
    }
  }

  if (definition.id === 'garmin-connect') {
    const password = String(completeConfig.password ?? '');
    if (password.length < 6) {
      return 'Password must be at least 6 characters.';
    }
  }

  if (definition.id === 'medical-report') {
    const provider = String(completeConfig.provider ?? '').trim();
    if (!['deepseek', 'volcengine'].includes(provider)) {
      return 'Provider must be DeepSeek or 火山引擎.';
    }
  }

  return null;
};

export const mockConnectorApi = {
  async list(): Promise<ApiResult<ConnectorRecord[]>> {
    await wait(120);

    return {
      success: true,
      data: readConnectors().map((connector) => cloneConnector(connector))
    };
  },

  async testConnection(payload: TestConnectorPayload): Promise<ApiResult<null>> {
    await wait(500);

    const definition = getConnectorDefinition(payload.id);
    const normalizedConfig = normalizePublicConnectorConfig(definition, payload.config);
    const requestedSecrets = normalizeSecretConfig(definition, payload.config);
    const storedConnector = readConnectors().find((connector) => connector.id === payload.id);
    const mergedSecrets = mergeSecretConfig(definition, requestedSecrets, storedConnector?.secrets ?? {});
    const validationError = validateConnectorConfig(definition, normalizedConfig, mergedSecrets);

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
    const normalizedConfig = normalizePublicConnectorConfig(definition, payload.config);
    const requestedSecrets = normalizeSecretConfig(definition, payload.config);
    const connectors = readConnectors();
    const connectorIndex = connectors.findIndex((connector) => connector.id === payload.id);

    if (connectorIndex === -1) {
      return {
        success: false,
        message: 'Connector not found.',
        code: 'CONNECTOR_NOT_FOUND'
      };
    }

    const mergedSecrets = mergeSecretConfig(definition, requestedSecrets, connectors[connectorIndex].secrets);
    const validationError = validateConnectorConfig(definition, normalizedConfig, mergedSecrets);
    if (validationError) {
      return {
        success: false,
        message: validationError,
        code: 'CONNECTOR_VALIDATION_ERROR'
      };
    }

    const isManualOnlyConnector = payload.id === 'medical-report';
    let normalizedSchedule = payload.schedule.trim();
    let normalizedNextRun = connectors[connectorIndex].nextRun;

    if (isManualOnlyConnector) {
      normalizedSchedule = '-';
      normalizedNextRun = '-';
    } else {
      const nextRunResult = getNextRunFromCron(normalizedSchedule);
      if (!nextRunResult.success || !nextRunResult.nextRun) {
        return {
          success: false,
          message: nextRunResult.message ?? 'Invalid cron expression.',
          code: 'INVALID_CRON'
        };
      }

      normalizedNextRun = formatDateTime(nextRunResult.nextRun);
    }

    const updatedConnector: StoredConnectorRecord = {
      ...connectors[connectorIndex],
      schedule: normalizedSchedule,
      nextRun: normalizedNextRun,
      config: normalizedConfig,
      secretFieldsConfigured: buildSecretFieldState(definition, mergedSecrets),
      secrets: mergedSecrets,
      status: normalizeConnectorStatus(definition, connectors[connectorIndex].status, normalizedConfig, mergedSecrets)
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

    if (!hasValidConfiguration(definition, connector.config, connector.secrets)) {
      return {
        success: false,
        message: 'Configure this connector before changing its status.',
        code: 'CONNECTOR_NOT_CONFIGURED'
      };
    }

    const updatedConnector: StoredConnectorRecord = {
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

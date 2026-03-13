import type { ConnectorDefinition, ConnectorId } from '~/types/connectors';

export const connectorCatalog: ConnectorDefinition[] = [
  {
    id: 'garmin-connect',
    name: 'Garmin Connect',
    category: 'health',
    fields: [
      {
        key: 'username',
        label: 'Username',
        type: 'text',
        placeholder: 'Enter your Garmin Connect username',
        required: true,
        autocomplete: 'username'
      },
      {
        key: 'password',
        label: 'Password',
        type: 'password',
        placeholder: 'Enter your Garmin Connect password',
        required: true,
        autocomplete: 'current-password'
      }
    ]
  },
  {
    id: 'medical-report',
    name: 'Medical Report',
    category: 'health',
    fields: [
      {
        key: 'provider',
        label: 'Provider',
        type: 'select',
        placeholder: 'Select provider',
        options: [
          {
            label: 'DeepSeek',
            value: 'deepseek',
            logo: '/brands/deepseek-color.svg'
          },
          {
            label: '火山引擎',
            value: 'volcengine',
            logo: '/brands/volcengine-color.svg'
          }
        ],
        required: true,
      },
      {
        key: 'modelId',
        label: 'Model ID',
        type: 'text',
        placeholder: 'Enter model ID',
        required: true
      },
      {
        key: 'apiKey',
        label: 'API Key',
        type: 'password',
        placeholder: 'Enter API key',
        required: true,
        autocomplete: 'off'
      }
    ]
  }
];

export const getConnectorDefinition = (id: ConnectorId): ConnectorDefinition | undefined => {
  return connectorCatalog.find((connector) => connector.id === id);
};

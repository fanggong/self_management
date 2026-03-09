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
  }
];

export const getConnectorDefinition = (id: ConnectorId): ConnectorDefinition | undefined => {
  return connectorCatalog.find((connector) => connector.id === id);
};

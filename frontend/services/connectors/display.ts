import garminConnectLogo from '~/assets/images/garmin-connect-tile-120.png';
import medicalReportLogo from '~/assets/images/medical-report-logo.png';
import { connectorCatalog } from '~/services/connectors/catalog';
import type { ConnectorId } from '~/types/connectors';

export type ConnectorDisplayIdentity = {
  id: ConnectorId;
  name: string;
  logo: string;
  fallbackIcon: string;
};

const CONNECTOR_DISPLAY_BY_ID: Record<ConnectorId, ConnectorDisplayIdentity> = {
  'garmin-connect': {
    id: 'garmin-connect',
    name: 'Garmin Connect',
    logo: garminConnectLogo,
    fallbackIcon: 'pi pi-link'
  },
  'medical-report': {
    id: 'medical-report',
    name: 'Medical Report',
    logo: medicalReportLogo,
    fallbackIcon: 'pi pi-file-medical'
  }
};

const CONNECTOR_ID_BY_NAME = new Map(
  connectorCatalog.map((connector) => [connector.name.trim().toLowerCase(), connector.id] as const)
);

export const resolveConnectorDisplayIdentity = (value: string | null | undefined): ConnectorDisplayIdentity | null => {
  const normalized = value?.trim().toLowerCase();
  if (!normalized) {
    return null;
  }

  const connectorById = CONNECTOR_DISPLAY_BY_ID[normalized as ConnectorId];
  if (connectorById) {
    return connectorById;
  }

  const connectorId = CONNECTOR_ID_BY_NAME.get(normalized);
  return connectorId ? CONNECTOR_DISPLAY_BY_ID[connectorId] : null;
};

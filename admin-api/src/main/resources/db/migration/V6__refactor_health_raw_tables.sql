CREATE TABLE IF NOT EXISTS raw.health_snapshot_record (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id UUID NOT NULL REFERENCES app.app_account(id),
  connector_config_id UUID NOT NULL REFERENCES app.connector_config(id),
  sync_task_id UUID NOT NULL REFERENCES app.sync_task(id),
  connector_id VARCHAR(120) NOT NULL,
  source_stream VARCHAR(120) NOT NULL,
  external_id VARCHAR(255) NOT NULL,
  source_record_date DATE NOT NULL,
  source_record_at TIMESTAMPTZ,
  source_updated_at TIMESTAMPTZ,
  payload_hash VARCHAR(128) NOT NULL,
  collected_at TIMESTAMPTZ NOT NULL,
  payload_jsonb JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (account_id, connector_config_id, source_stream, source_record_date, external_id)
);

CREATE TABLE IF NOT EXISTS raw.health_event_record (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id UUID NOT NULL REFERENCES app.app_account(id),
  connector_config_id UUID NOT NULL REFERENCES app.connector_config(id),
  sync_task_id UUID NOT NULL REFERENCES app.sync_task(id),
  connector_id VARCHAR(120) NOT NULL,
  source_stream VARCHAR(120) NOT NULL,
  external_id VARCHAR(255) NOT NULL,
  source_record_date DATE NOT NULL,
  source_record_at TIMESTAMPTZ,
  source_updated_at TIMESTAMPTZ,
  payload_hash VARCHAR(128) NOT NULL,
  collected_at TIMESTAMPTZ NOT NULL,
  payload_jsonb JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (account_id, connector_config_id, source_stream, source_record_date, external_id)
);

CREATE TABLE IF NOT EXISTS raw.health_timeseries_record (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id UUID NOT NULL REFERENCES app.app_account(id),
  connector_config_id UUID NOT NULL REFERENCES app.connector_config(id),
  sync_task_id UUID NOT NULL REFERENCES app.sync_task(id),
  connector_id VARCHAR(120) NOT NULL,
  source_stream VARCHAR(120) NOT NULL,
  external_id VARCHAR(255) NOT NULL,
  source_record_date DATE NOT NULL,
  source_record_at TIMESTAMPTZ,
  source_updated_at TIMESTAMPTZ,
  payload_hash VARCHAR(128) NOT NULL,
  collected_at TIMESTAMPTZ NOT NULL,
  payload_jsonb JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (account_id, connector_config_id, source_stream, source_record_date, external_id)
);

CREATE INDEX IF NOT EXISTS idx_health_snapshot_record_lookup
  ON raw.health_snapshot_record(account_id, connector_id, source_stream, source_record_date);

CREATE INDEX IF NOT EXISTS idx_health_event_record_lookup
  ON raw.health_event_record(account_id, connector_id, source_stream, source_record_date);

CREATE INDEX IF NOT EXISTS idx_health_timeseries_record_lookup
  ON raw.health_timeseries_record(account_id, connector_id, source_stream, source_record_date);

INSERT INTO raw.health_snapshot_record (
  account_id,
  connector_config_id,
  sync_task_id,
  connector_id,
  source_stream,
  external_id,
  source_record_date,
  source_record_at,
  source_updated_at,
  payload_hash,
  collected_at,
  payload_jsonb,
  created_at,
  updated_at
)
SELECT
  account_id,
  connector_config_id,
  sync_task_id,
  'garmin-connect',
  'profile',
  external_id,
  source_record_date,
  NULL,
  source_updated_at,
  payload_hash,
  collected_at,
  CASE
    WHEN payload_jsonb ? 'raw' THEN payload_jsonb -> 'raw'
    ELSE payload_jsonb
  END,
  created_at,
  updated_at
FROM raw.raw_garmin_profile
ON CONFLICT (account_id, connector_config_id, source_stream, source_record_date, external_id) DO NOTHING;

INSERT INTO raw.health_snapshot_record (
  account_id,
  connector_config_id,
  sync_task_id,
  connector_id,
  source_stream,
  external_id,
  source_record_date,
  source_record_at,
  source_updated_at,
  payload_hash,
  collected_at,
  payload_jsonb,
  created_at,
  updated_at
)
SELECT
  account_id,
  connector_config_id,
  sync_task_id,
  'garmin-connect',
  'daily_summary',
  external_id,
  source_record_date,
  NULL,
  source_updated_at,
  payload_hash,
  collected_at,
  CASE
    WHEN payload_jsonb ? 'raw' THEN payload_jsonb -> 'raw'
    ELSE payload_jsonb
  END,
  created_at,
  updated_at
FROM raw.raw_garmin_daily_summary
ON CONFLICT (account_id, connector_config_id, source_stream, source_record_date, external_id) DO NOTHING;

INSERT INTO raw.health_snapshot_record (
  account_id,
  connector_config_id,
  sync_task_id,
  connector_id,
  source_stream,
  external_id,
  source_record_date,
  source_record_at,
  source_updated_at,
  payload_hash,
  collected_at,
  payload_jsonb,
  created_at,
  updated_at
)
SELECT
  account_id,
  connector_config_id,
  sync_task_id,
  'garmin-connect',
  'sleep',
  external_id,
  source_record_date,
  sleep_start_at,
  source_updated_at,
  payload_hash,
  collected_at,
  CASE
    WHEN payload_jsonb ? 'raw' THEN payload_jsonb -> 'raw'
    ELSE payload_jsonb
  END,
  created_at,
  updated_at
FROM raw.raw_garmin_sleep
ON CONFLICT (account_id, connector_config_id, source_stream, source_record_date, external_id) DO NOTHING;

INSERT INTO raw.health_event_record (
  account_id,
  connector_config_id,
  sync_task_id,
  connector_id,
  source_stream,
  external_id,
  source_record_date,
  source_record_at,
  source_updated_at,
  payload_hash,
  collected_at,
  payload_jsonb,
  created_at,
  updated_at
)
SELECT
  account_id,
  connector_config_id,
  sync_task_id,
  'garmin-connect',
  'activity',
  external_id,
  source_record_date,
  start_at,
  source_updated_at,
  payload_hash,
  collected_at,
  CASE
    WHEN payload_jsonb ? 'raw' THEN payload_jsonb -> 'raw'
    ELSE payload_jsonb
  END,
  created_at,
  updated_at
FROM raw.raw_garmin_activity
ON CONFLICT (account_id, connector_config_id, source_stream, source_record_date, external_id) DO NOTHING;

INSERT INTO raw.health_timeseries_record (
  account_id,
  connector_config_id,
  sync_task_id,
  connector_id,
  source_stream,
  external_id,
  source_record_date,
  source_record_at,
  source_updated_at,
  payload_hash,
  collected_at,
  payload_jsonb,
  created_at,
  updated_at
)
SELECT
  account_id,
  connector_config_id,
  sync_task_id,
  'garmin-connect',
  'heart_rate',
  external_id,
  source_record_date,
  CASE
    WHEN NULLIF((CASE WHEN payload_jsonb ? 'raw' THEN payload_jsonb -> 'raw' ELSE payload_jsonb END) ->> 'startTimestampGMT', '') ~ '^[0-9]{13}$'
      THEN to_timestamp(((CASE WHEN payload_jsonb ? 'raw' THEN payload_jsonb -> 'raw' ELSE payload_jsonb END) ->> 'startTimestampGMT')::numeric / 1000.0)
    WHEN NULLIF((CASE WHEN payload_jsonb ? 'raw' THEN payload_jsonb -> 'raw' ELSE payload_jsonb END) ->> 'startTimestampGMT', '') IS NOT NULL
      THEN ((CASE WHEN payload_jsonb ? 'raw' THEN payload_jsonb -> 'raw' ELSE payload_jsonb END) ->> 'startTimestampGMT')::timestamptz
    ELSE NULL
  END,
  source_updated_at,
  payload_hash,
  collected_at,
  CASE
    WHEN payload_jsonb ? 'raw' THEN payload_jsonb -> 'raw'
    ELSE payload_jsonb
  END,
  created_at,
  updated_at
FROM raw.raw_garmin_heart_rate
ON CONFLICT (account_id, connector_config_id, source_stream, source_record_date, external_id) DO NOTHING;

DROP TABLE IF EXISTS raw.raw_garmin_profile CASCADE;
DROP TABLE IF EXISTS raw.raw_garmin_daily_summary CASCADE;
DROP TABLE IF EXISTS raw.raw_garmin_activity CASCADE;
DROP TABLE IF EXISTS raw.raw_garmin_sleep CASCADE;
DROP TABLE IF EXISTS raw.raw_garmin_heart_rate CASCADE;

CREATE TABLE IF NOT EXISTS raw.raw_garmin_activity (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id UUID NOT NULL REFERENCES app.app_account(id),
  connector_config_id UUID NOT NULL REFERENCES app.connector_config(id),
  sync_task_id UUID NOT NULL REFERENCES app.sync_task(id),
  external_id VARCHAR(255) NOT NULL,
  source_record_date DATE NOT NULL,
  source_updated_at TIMESTAMPTZ,
  activity_id VARCHAR(64),
  activity_name VARCHAR(255),
  activity_type VARCHAR(120),
  start_at TIMESTAMPTZ,
  duration_seconds INTEGER,
  distance_meters NUMERIC(18, 2),
  calories_kcal NUMERIC(18, 2),
  average_heart_rate INTEGER,
  max_heart_rate INTEGER,
  steps INTEGER,
  payload_hash VARCHAR(128) NOT NULL,
  collected_at TIMESTAMPTZ NOT NULL,
  payload_jsonb JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (account_id, connector_config_id, source_record_date, external_id)
);

CREATE TABLE IF NOT EXISTS raw.raw_garmin_sleep (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id UUID NOT NULL REFERENCES app.app_account(id),
  connector_config_id UUID NOT NULL REFERENCES app.connector_config(id),
  sync_task_id UUID NOT NULL REFERENCES app.sync_task(id),
  external_id VARCHAR(255) NOT NULL,
  source_record_date DATE NOT NULL,
  source_updated_at TIMESTAMPTZ,
  sleep_id VARCHAR(64),
  sleep_start_at TIMESTAMPTZ,
  sleep_end_at TIMESTAMPTZ,
  sleep_time_seconds INTEGER,
  nap_time_seconds INTEGER,
  deep_sleep_seconds INTEGER,
  light_sleep_seconds INTEGER,
  rem_sleep_seconds INTEGER,
  awake_sleep_seconds INTEGER,
  payload_hash VARCHAR(128) NOT NULL,
  collected_at TIMESTAMPTZ NOT NULL,
  payload_jsonb JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (account_id, connector_config_id, source_record_date, external_id)
);

CREATE TABLE IF NOT EXISTS raw.raw_garmin_heart_rate (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id UUID NOT NULL REFERENCES app.app_account(id),
  connector_config_id UUID NOT NULL REFERENCES app.connector_config(id),
  sync_task_id UUID NOT NULL REFERENCES app.sync_task(id),
  external_id VARCHAR(255) NOT NULL,
  source_record_date DATE NOT NULL,
  source_updated_at TIMESTAMPTZ,
  resting_heart_rate INTEGER,
  min_heart_rate INTEGER,
  max_heart_rate INTEGER,
  average_heart_rate NUMERIC(10, 2),
  sample_count INTEGER,
  payload_hash VARCHAR(128) NOT NULL,
  collected_at TIMESTAMPTZ NOT NULL,
  payload_jsonb JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (account_id, connector_config_id, source_record_date, external_id)
);

CREATE INDEX IF NOT EXISTS idx_raw_garmin_activity_date
  ON raw.raw_garmin_activity(account_id, source_record_date);

CREATE INDEX IF NOT EXISTS idx_raw_garmin_sleep_date
  ON raw.raw_garmin_sleep(account_id, source_record_date);

CREATE INDEX IF NOT EXISTS idx_raw_garmin_heart_rate_date
  ON raw.raw_garmin_heart_rate(account_id, source_record_date);

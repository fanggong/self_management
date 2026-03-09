CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE app_account (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(120) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE app_user (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id UUID NOT NULL REFERENCES app_account(id),
  principal VARCHAR(120) NOT NULL UNIQUE,
  display_name VARCHAR(120) NOT NULL,
  email VARCHAR(255) NOT NULL DEFAULT '',
  phone VARCHAR(64) NOT NULL DEFAULT '',
  avatar_url TEXT NOT NULL DEFAULT '',
  role VARCHAR(32) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE connector_config (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id UUID NOT NULL REFERENCES app_account(id),
  connector_id VARCHAR(120) NOT NULL,
  category VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  schedule VARCHAR(64) NOT NULL,
  last_run_at TIMESTAMPTZ,
  next_run_at TIMESTAMPTZ,
  config_ciphertext TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (account_id, connector_id)
);

CREATE TABLE sync_task (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id UUID NOT NULL REFERENCES app_account(id),
  connector_config_id UUID NOT NULL REFERENCES connector_config(id),
  trigger_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  window_start_at TIMESTAMPTZ,
  window_end_at TIMESTAMPTZ,
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  fetched_count INTEGER NOT NULL DEFAULT 0,
  inserted_count INTEGER NOT NULL DEFAULT 0,
  updated_count INTEGER NOT NULL DEFAULT 0,
  unchanged_count INTEGER NOT NULL DEFAULT 0,
  deduped_count INTEGER NOT NULL DEFAULT 0,
  error_code VARCHAR(64),
  error_message TEXT,
  dispatched_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_log (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id UUID NOT NULL REFERENCES app_account(id),
  user_id UUID REFERENCES app_user(id),
  action VARCHAR(120) NOT NULL,
  target_type VARCHAR(120) NOT NULL,
  target_id VARCHAR(120) NOT NULL,
  payload_jsonb JSONB NOT NULL DEFAULT '{}'::JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE raw_ingest_event (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id UUID NOT NULL REFERENCES app_account(id),
  connector_config_id UUID NOT NULL REFERENCES connector_config(id),
  sync_task_id UUID NOT NULL REFERENCES sync_task(id),
  domain VARCHAR(64) NOT NULL,
  connector VARCHAR(64) NOT NULL,
  entity_type VARCHAR(64) NOT NULL,
  external_id VARCHAR(255) NOT NULL,
  source_record_date DATE NOT NULL,
  source_record_ts TIMESTAMPTZ,
  source_created_at TIMESTAMPTZ,
  source_updated_at TIMESTAMPTZ,
  payload_hash VARCHAR(128) NOT NULL,
  collected_at TIMESTAMPTZ NOT NULL,
  payload_jsonb JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (account_id, connector_config_id, entity_type, source_record_date, external_id)
);

CREATE TABLE raw_sync_task_snapshot (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id UUID NOT NULL REFERENCES app_account(id),
  connector_config_id UUID NOT NULL REFERENCES connector_config(id),
  sync_task_id UUID NOT NULL REFERENCES sync_task(id),
  snapshot_jsonb JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE raw_garmin_profile (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id UUID NOT NULL REFERENCES app_account(id),
  connector_config_id UUID NOT NULL REFERENCES connector_config(id),
  sync_task_id UUID NOT NULL REFERENCES sync_task(id),
  external_id VARCHAR(255) NOT NULL,
  source_record_date DATE NOT NULL,
  source_updated_at TIMESTAMPTZ,
  payload_hash VARCHAR(128) NOT NULL,
  collected_at TIMESTAMPTZ NOT NULL,
  payload_jsonb JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (account_id, connector_config_id, source_record_date, external_id)
);

CREATE TABLE raw_garmin_daily_summary (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id UUID NOT NULL REFERENCES app_account(id),
  connector_config_id UUID NOT NULL REFERENCES connector_config(id),
  sync_task_id UUID NOT NULL REFERENCES sync_task(id),
  external_id VARCHAR(255) NOT NULL,
  source_record_date DATE NOT NULL,
  source_updated_at TIMESTAMPTZ,
  steps INTEGER,
  distance_meters NUMERIC(18, 2),
  calories_kcal NUMERIC(18, 2),
  active_minutes INTEGER,
  payload_hash VARCHAR(128) NOT NULL,
  collected_at TIMESTAMPTZ NOT NULL,
  payload_jsonb JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (account_id, connector_config_id, source_record_date, external_id)
);

CREATE TABLE raw_garmin_activity (
  LIKE raw_garmin_profile INCLUDING ALL
);
CREATE TABLE raw_garmin_activity_detail (
  LIKE raw_garmin_profile INCLUDING ALL
);
CREATE TABLE raw_garmin_sleep (
  LIKE raw_garmin_profile INCLUDING ALL
);
CREATE TABLE raw_garmin_heart_rate (
  LIKE raw_garmin_profile INCLUDING ALL
);
CREATE TABLE raw_garmin_respiration (
  LIKE raw_garmin_profile INCLUDING ALL
);
CREATE TABLE raw_garmin_spo2 (
  LIKE raw_garmin_profile INCLUDING ALL
);
CREATE TABLE raw_garmin_stress (
  LIKE raw_garmin_profile INCLUDING ALL
);
CREATE TABLE raw_garmin_body_composition (
  LIKE raw_garmin_profile INCLUDING ALL
);
CREATE TABLE raw_garmin_training_metrics (
  LIKE raw_garmin_profile INCLUDING ALL
);

CREATE INDEX idx_connector_config_account ON connector_config(account_id);
CREATE INDEX idx_sync_task_account_created ON sync_task(account_id, created_at DESC);
CREATE INDEX idx_sync_task_dispatch ON sync_task(status, dispatched_at, created_at);
CREATE INDEX idx_raw_garmin_daily_summary_date ON raw_garmin_daily_summary(account_id, source_record_date);

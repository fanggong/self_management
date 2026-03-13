INSERT INTO app.connector_config (
  account_id,
  connector_id,
  category,
  status,
  schedule,
  last_run_at,
  next_run_at,
  config_ciphertext
)
SELECT
  account.id,
  'medical-report',
  'health',
  'not_configured',
  '-',
  NULL,
  NULL,
  NULL
FROM app.app_account account
WHERE NOT EXISTS (
  SELECT 1
  FROM app.connector_config config
  WHERE config.account_id = account.id
    AND config.connector_id = 'medical-report'
);

CREATE TABLE IF NOT EXISTS app.medical_report_parse_session (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id UUID NOT NULL REFERENCES app.app_account(id),
  connector_config_id UUID NOT NULL REFERENCES app.connector_config(id),
  provider VARCHAR(64) NOT NULL,
  model_id VARCHAR(128) NOT NULL,
  record_number VARCHAR(128) NOT NULL,
  report_date DATE NOT NULL,
  institution VARCHAR(255) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  file_hash VARCHAR(128) NOT NULL,
  parsed_payload_jsonb JSONB NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_medical_report_parse_session_account
  ON app.medical_report_parse_session(account_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_medical_report_parse_session_expires
  ON app.medical_report_parse_session(expires_at);

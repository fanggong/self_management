CREATE TABLE app.dbt_run_history (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id UUID NOT NULL REFERENCES app.app_account(id),
  user_id UUID NOT NULL REFERENCES app.app_user(id),
  requested_layer VARCHAR(32) NOT NULL,
  requested_model_name VARCHAR(255) NOT NULL,
  selector TEXT NOT NULL,
  status VARCHAR(32) NOT NULL,
  returncode INTEGER,
  stdout TEXT NOT NULL DEFAULT '',
  stderr TEXT NOT NULL DEFAULT '',
  error_code VARCHAR(64),
  error_message TEXT,
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE app.dbt_run_model_history (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  dbt_run_history_id UUID NOT NULL REFERENCES app.dbt_run_history(id) ON DELETE CASCADE,
  account_id UUID NOT NULL REFERENCES app.app_account(id),
  layer VARCHAR(32) NOT NULL,
  model_name VARCHAR(255) NOT NULL,
  unique_id VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL,
  message TEXT,
  relation_name TEXT,
  execution_time_seconds DOUBLE PRECISION,
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dbt_run_history_account_created
  ON app.dbt_run_history(account_id, created_at DESC);

CREATE INDEX idx_dbt_run_model_history_run_id
  ON app.dbt_run_model_history(dbt_run_history_id);

CREATE INDEX idx_dbt_run_model_history_account_layer_model_success_completed
  ON app.dbt_run_model_history(account_id, layer, model_name, completed_at DESC)
  WHERE status = 'success';

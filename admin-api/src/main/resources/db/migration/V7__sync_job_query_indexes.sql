CREATE INDEX IF NOT EXISTS idx_sync_task_account_created
  ON app.sync_task(account_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_sync_task_account_status_created
  ON app.sync_task(account_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_sync_task_account_trigger_created
  ON app.sync_task(account_id, trigger_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_sync_task_account_connector_created
  ON app.sync_task(account_id, connector_config_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_connector_config_account_category_connector
  ON app.connector_config(account_id, category, connector_id);

ALTER TABLE app.medical_report_parse_session
  ADD COLUMN IF NOT EXISTS confirmed_payload_jsonb JSONB;

ALTER TABLE app.medical_report_parse_session
  ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'parsed';

UPDATE app.medical_report_parse_session
SET status = 'parsed'
WHERE status IS NULL OR status = '';

ALTER TABLE app.sync_task
  ADD COLUMN IF NOT EXISTS parse_session_id UUID REFERENCES app.medical_report_parse_session(id);

CREATE INDEX IF NOT EXISTS idx_medical_report_parse_session_status
  ON app.medical_report_parse_session(account_id, status, expires_at);

CREATE INDEX IF NOT EXISTS idx_sync_task_parse_session
  ON app.sync_task(parse_session_id);

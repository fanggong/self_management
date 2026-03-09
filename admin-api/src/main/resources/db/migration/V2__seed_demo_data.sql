INSERT INTO app_account (id, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'Demo Account')
ON CONFLICT (id) DO NOTHING;

INSERT INTO app_user (
  id,
  account_id,
  principal,
  display_name,
  email,
  phone,
  avatar_url,
  role,
  password_hash
)
VALUES (
  '00000000-0000-0000-0000-000000000101',
  '00000000-0000-0000-0000-000000000001',
  'demo',
  'Admin',
  'admin@otw.local',
  '+1 555 010 0001',
  '',
  'ADMIN',
  '$2b$12$L6wnGw3Za5cnjaNu1umnhud6rlWrJTRjFsTqL.W/xKC/o.PqVFD0W'
)
ON CONFLICT (principal) DO NOTHING;

INSERT INTO connector_config (
  id,
  account_id,
  connector_id,
  category,
  status,
  schedule,
  last_run_at,
  next_run_at,
  config_ciphertext
)
VALUES (
  '00000000-0000-0000-0000-000000000201',
  '00000000-0000-0000-0000-000000000001',
  'garmin-connect',
  'health',
  'not_configured',
  '0 2 * * *',
  '2026-03-05 02:00:00+08',
  '2026-03-06 02:00:00+08',
  NULL
)
ON CONFLICT (account_id, connector_id) DO NOTHING;

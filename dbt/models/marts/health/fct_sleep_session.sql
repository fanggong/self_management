{{ config(tags=['garmin', 'health', 'mart']) }}

select
  account_id,
  connector_config_id,
  sleep_external_id,
  sleep_id,
  source_record_date,
  sleep_start_at,
  sleep_end_at,
  sleep_time_seconds,
  nap_time_seconds,
  deep_sleep_seconds,
  light_sleep_seconds,
  rem_sleep_seconds,
  awake_sleep_seconds,
  sleep_hours
from {{ ref('int_sleep_enriched') }}

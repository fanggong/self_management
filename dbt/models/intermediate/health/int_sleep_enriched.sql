{{ config(tags=['garmin', 'health', 'intermediate']) }}

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
  round(coalesce(sleep_time_seconds, 0) / 3600.0, 2) as sleep_hours
from {{ ref('stg_garmin_sleep') }}

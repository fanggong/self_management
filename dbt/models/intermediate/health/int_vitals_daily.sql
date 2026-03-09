{{ config(tags=['garmin', 'health', 'intermediate']) }}

select
  account_id,
  source_record_date,
  steps,
  distance_meters,
  calories_kcal,
  active_minutes,
  sleep_time_seconds,
  deep_sleep_seconds,
  light_sleep_seconds,
  rem_sleep_seconds,
  awake_sleep_seconds,
  resting_heart_rate,
  min_heart_rate,
  max_heart_rate,
  average_heart_rate,
  heart_rate_sample_count
from {{ ref('int_health_daily_user') }}

{{ config(tags=['garmin', 'health', 'mart']) }}

select
  metrics.account_id,
  metrics.source_record_date,
  profile.display_name,
  metrics.steps,
  metrics.distance_meters,
  metrics.calories_kcal,
  metrics.active_minutes,
  metrics.activity_count,
  metrics.activity_duration_seconds,
  metrics.activity_distance_meters,
  metrics.sleep_time_seconds,
  metrics.deep_sleep_seconds,
  metrics.light_sleep_seconds,
  metrics.rem_sleep_seconds,
  metrics.resting_heart_rate,
  metrics.min_heart_rate,
  metrics.max_heart_rate,
  metrics.average_heart_rate
from {{ ref('fct_health_daily_metrics') }} metrics
left join {{ ref('dim_user_health_profile') }} profile
  on metrics.account_id = profile.account_id

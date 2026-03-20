{{ config(tags=['intermediate', 'health', 'garmin']) }}

select
  {{ health_intermediate_common_columns('stg') }},
  stg.user_profile_pk,
  {{ health_try_date('stg.calendar_date') }} as calendar_date,
  {{ health_try_utc_timestamptz('stg.start_timestamp_gmt') }} as start_timestamp_gmt,
  {{ health_try_utc_timestamptz('stg.end_timestamp_gmt') }} as end_timestamp_gmt,
  stg.start_timestamp_local,
  stg.end_timestamp_local,
  stg.resting_heart_rate,
  stg.min_heart_rate,
  stg.max_heart_rate,
  stg.last_seven_days_avg_resting_heart_rate
from {{ ref('stg_garmin_heart_rate_summary') }} as stg

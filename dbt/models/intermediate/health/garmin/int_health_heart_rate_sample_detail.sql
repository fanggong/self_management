{{ config(tags=['intermediate', 'health', 'garmin']) }}

select
  {{ health_intermediate_common_columns('stg') }},
  {{ health_try_date('stg.calendar_date') }} as calendar_date,
  stg.sample_position,
  {{ health_try_utc_timestamptz('stg.sample_timestamp') }} as sample_timestamp,
  stg.heart_rate_value
from {{ ref('stg_garmin_heart_rate_sample_detail') }} as stg

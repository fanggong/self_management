{{ config(tags=['intermediate', 'health', 'garmin']) }}

select
  {{ health_intermediate_common_columns('stg') }},
  stg.sleep_id,
  stg.metric_name,
  stg.sample_position,
  {{ health_try_utc_timestamptz('stg.sample_at') }} as sample_at,
  stg.value
from {{ ref('stg_garmin_sleep_metric_detail') }} as stg

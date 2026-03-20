{{ config(tags=['intermediate', 'health', 'garmin']) }}

select
  {{ health_intermediate_common_columns('stg') }},
  stg.sleep_id,
  stg.stage_name,
  stg.stage_position,
  {{ health_try_utc_timestamptz('stg.stage_start_at') }} as stage_start_at,
  {{ health_try_utc_timestamptz('stg.stage_end_at') }} as stage_end_at,
  {{ health_try_integer('stg.activity_level') }} as activity_level
from {{ ref('stg_garmin_sleep_stage_detail') }} as stg

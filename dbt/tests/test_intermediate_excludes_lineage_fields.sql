with _dependency as (
    select 1 from {{ ref('int_health_profile_snapshot') }} where false
    union all select 1 from {{ ref('int_health_profile_attribute_detail') }} where false
    union all select 1 from {{ ref('int_health_daily_summary') }} where false
    union all select 1 from {{ ref('int_health_activity_detail') }} where false
    union all select 1 from {{ ref('int_health_sleep_summary') }} where false
    union all select 1 from {{ ref('int_health_sleep_stage_detail') }} where false
    union all select 1 from {{ ref('int_health_sleep_metric_detail') }} where false
    union all select 1 from {{ ref('int_health_heart_rate_summary') }} where false
    union all select 1 from {{ ref('int_health_heart_rate_sample_detail') }} where false
    union all select 1 from {{ ref('int_health_medical_report_snapshot') }} where false
    union all select 1 from {{ ref('int_health_medical_report_item_detail') }} where false
)
select
  table_name as model_name,
  column_name
from information_schema.columns
where table_schema = 'intermediate'
  and table_name like 'int_health_%'
  and column_name in (
    'connector_id',
    'connector_config_id',
    'sync_task_id',
    'source_stream',
    'payload_hash',
    'collected_at',
    'raw_created_at',
    'raw_updated_at',
    'raw_record_id',
    'provider',
    'region',
    'parse_session_id',
    'model_id'
  )

with _dependency as (
    select 1 from {{ ref('stg_garmin_daily_summary') }} where false
    union all select 1 from {{ ref('stg_garmin_activity_detail') }} where false
    union all select 1 from {{ ref('stg_garmin_activity_role_detail') }} where false
    union all select 1 from {{ ref('stg_garmin_sleep_stage_detail') }} where false
    union all select 1 from {{ ref('stg_garmin_sleep_summary') }} where false
    union all select 1 from {{ ref('stg_garmin_sleep_metric_detail') }} where false
    union all select 1 from {{ ref('stg_garmin_heart_rate_summary') }} where false
    union all select 1 from {{ ref('stg_garmin_heart_rate_sample_detail') }} where false
    union all select 1 from {{ ref('stg_medical_report_snapshot') }} where false
    union all select 1 from {{ ref('stg_medical_report_item_detail') }} where false
),
expected as (
    select 'stg_garmin_daily_summary'::text as model_name, 'calendar_date'::text as column_name, 'text'::text as udt_name
    union all select 'stg_garmin_daily_summary', 'total_steps', 'int8'
    union all select 'stg_garmin_daily_summary', 'includes_wellness_data', 'bool'
    union all select 'stg_garmin_activity_detail', 'start_time_gmt', 'text'
    union all select 'stg_garmin_activity_detail', 'distance', 'numeric'
    union all select 'stg_garmin_activity_role_detail', 'item_position', 'int8'
    union all select 'stg_garmin_sleep_stage_detail', 'stage_start_at', 'text'
    union all select 'stg_garmin_sleep_stage_detail', 'activity_level', 'numeric'
    union all select 'stg_garmin_sleep_summary', 'rem_sleep_data', 'bool'
    union all select 'stg_garmin_sleep_metric_detail', 'sample_at', 'text'
    union all select 'stg_garmin_heart_rate_summary', 'calendar_date', 'text'
    union all select 'stg_garmin_heart_rate_sample_detail', 'sample_timestamp', 'text'
    union all select 'stg_garmin_heart_rate_sample_detail', 'heart_rate_value', 'int4'
    union all select 'stg_medical_report_snapshot', 'report_date', 'text'
    union all select 'stg_medical_report_item_detail', 'section_exam_date', 'text'
    union all select 'stg_medical_report_item_detail', 'result', 'text'
),
actual as (
    select
      table_name as model_name,
      column_name,
      udt_name
    from information_schema.columns
    where table_schema = 'staging'
      and (table_name, column_name) in (
        select model_name, column_name from expected
      )
),
diff as (
    (select * from actual except select * from expected)
    union all
    (select * from expected except select * from actual)
)
select *
from diff

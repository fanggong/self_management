with _dependency as (
    select 1 from {{ ref('int_health_daily_summary') }} where false
    union all select 1 from {{ ref('int_health_activity_detail') }} where false
    union all select 1 from {{ ref('int_health_sleep_summary') }} where false
    union all select 1 from {{ ref('int_health_sleep_stage_detail') }} where false
    union all select 1 from {{ ref('int_health_sleep_metric_detail') }} where false
    union all select 1 from {{ ref('int_health_heart_rate_summary') }} where false
    union all select 1 from {{ ref('int_health_heart_rate_sample_detail') }} where false
    union all select 1 from {{ ref('int_health_medical_report_snapshot') }} where false
    union all select 1 from {{ ref('int_health_medical_report_item_detail') }} where false
),
expected as (
    select 'int_health_daily_summary'::text as model_name, 'calendar_date'::text as column_name, 'date'::text as data_type, 'date'::text as udt_name
    union all select 'int_health_daily_summary', 'wellness_start_time_gmt', 'timestamp with time zone', 'timestamptz'
    union all select 'int_health_daily_summary', 'wellness_start_time_local', 'text', 'text'
    union all select 'int_health_activity_detail', 'start_time_gmt', 'timestamp with time zone', 'timestamptz'
    union all select 'int_health_activity_detail', 'start_time_local', 'text', 'text'
    union all select 'int_health_activity_detail', 'water_estimated', 'numeric', 'numeric'
    union all select 'int_health_sleep_summary', 'calendar_date', 'date', 'date'
    union all select 'int_health_sleep_summary', 'sleep_start_timestamp_gmt', 'timestamp with time zone', 'timestamptz'
    union all select 'int_health_sleep_summary', 'sleep_end_timestamp_local', 'timestamp with time zone', 'timestamptz'
    union all select 'int_health_sleep_stage_detail', 'stage_start_at', 'timestamp with time zone', 'timestamptz'
    union all select 'int_health_sleep_stage_detail', 'activity_level', 'integer', 'int4'
    union all select 'int_health_sleep_metric_detail', 'sample_at', 'timestamp with time zone', 'timestamptz'
    union all select 'int_health_heart_rate_summary', 'calendar_date', 'date', 'date'
    union all select 'int_health_heart_rate_summary', 'start_timestamp_gmt', 'timestamp with time zone', 'timestamptz'
    union all select 'int_health_heart_rate_summary', 'start_timestamp_local', 'text', 'text'
    union all select 'int_health_heart_rate_sample_detail', 'sample_timestamp', 'timestamp with time zone', 'timestamptz'
    union all select 'int_health_medical_report_snapshot', 'report_date', 'date', 'date'
    union all select 'int_health_medical_report_item_detail', 'report_date', 'date', 'date'
    union all select 'int_health_medical_report_item_detail', 'section_exam_date', 'date', 'date'
    union all select 'int_health_medical_report_item_detail', 'result', 'text', 'text'
    union all select 'int_health_medical_report_item_detail', 'reference_value_min', 'numeric', 'numeric'
    union all select 'int_health_medical_report_item_detail', 'reference_value_max', 'numeric', 'numeric'
),
actual as (
    select
      table_name as model_name,
      column_name,
      data_type,
      udt_name
    from information_schema.columns
    where table_schema = 'intermediate'
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

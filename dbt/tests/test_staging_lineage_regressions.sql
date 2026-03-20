with _dependency as (
    select 1 from {{ ref('stg_garmin_activity_detail') }} where false
    union all select 1 from {{ ref('stg_garmin_sleep_summary') }} where false
    union all select 1 from {{ ref('stg_garmin_sleep_stage_detail') }} where false
    union all select 1 from {{ ref('stg_garmin_sleep_metric_detail') }} where false
    union all select 1 from {{ ref('stg_garmin_heart_rate_summary') }} where false
    union all select 1 from {{ ref('stg_medical_report_snapshot') }} where false
),
issues as (
    select
      'missing_activity_event_type_leaf'::text as issue_type,
      'stg_garmin_activity_detail'::text as model_name,
      'event_type_type_id/event_type_type_key/event_type_sort_order'::text as detail
    where not exists (
      select 1
      from information_schema.columns
      where table_schema = 'staging'
        and table_name = 'stg_garmin_activity_detail'
        and column_name in ('event_type_type_id', 'event_type_type_key', 'event_type_sort_order')
      group by table_name
      having count(*) = 3
    )

    union all

    select
      'unexpected_activity_synthetic_column',
      'stg_garmin_activity_detail',
      column_name
    from information_schema.columns
    where table_schema = 'staging'
      and table_name = 'stg_garmin_activity_detail'
      and column_name in ('event_type', 'activity_type', 'activity_type_type_name')

    union all

    select
      'unexpected_sleep_need_column',
      'stg_garmin_sleep_summary',
      column_name
    from information_schema.columns
    where table_schema = 'staging'
      and table_name = 'stg_garmin_sleep_summary'
      and (
        column_name like 'sleep_need_%'
        or column_name like 'next_sleep_need_%'
        or column_name in ('wellness_epoch_spo2_sample_count', 'wellness_spo2_sleep_summary_present')
      )

    union all

    select
      'missing_sleep_movement_stage_rows',
      'stg_garmin_sleep_stage_detail',
      'sleepMovement'
    where not exists (
      select 1
      from {{ ref('stg_garmin_sleep_stage_detail') }}
      where stage_name = 'sleepMovement'
        and stage_start_at is not null
        and stage_end_at is not null
    )

    union all

    select
      'unexpected_sleep_movement_metric_rows',
      'stg_garmin_sleep_metric_detail',
      'sleepMovement'
    where exists (
      select 1
      from {{ ref('stg_garmin_sleep_metric_detail') }}
      where metric_name = 'sleepMovement'
    )

    union all

    select
      'missing_rem_sleep_data_column',
      'stg_garmin_sleep_summary',
      'rem_sleep_data'
    where not exists (
      select 1
      from information_schema.columns
      where table_schema = 'staging'
        and table_name = 'stg_garmin_sleep_summary'
        and column_name = 'rem_sleep_data'
    )

    union all

    select
      'unexpected_heart_rate_summary_count_column',
      'stg_garmin_heart_rate_summary',
      column_name
    from information_schema.columns
    where table_schema = 'staging'
      and table_name = 'stg_garmin_heart_rate_summary'
      and column_name in ('sample_count', 'descriptor_count', 'sample_row_count', 'average_heart_rate')

    union all

    select
      'unexpected_medical_report_section_count',
      'stg_medical_report_snapshot',
      'section_count'
    where exists (
      select 1
      from information_schema.columns
      where table_schema = 'staging'
        and table_name = 'stg_medical_report_snapshot'
        and column_name = 'section_count'
    )
)
select *
from issues

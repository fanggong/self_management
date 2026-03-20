with activity_diff as (
    select
      'int_health_activity_detail'::text as model_name,
      'start_time_gmt'::text as field_name,
      stg.raw_record_id as staging_record_id
    from staging.stg_garmin_activity_detail stg
    join {{ ref('int_health_activity_detail') }} int
      on int.staging_record_id = stg.raw_record_id
    where stg.start_time_gmt is not null
      and int.start_time_gmt is distinct from (stg.start_time_gmt::timestamp at time zone 'UTC')
),
daily_diff as (
    select
      'int_health_daily_summary'::text as model_name,
      'calendar_date'::text as field_name,
      stg.raw_record_id as staging_record_id
    from staging.stg_garmin_daily_summary stg
    join {{ ref('int_health_daily_summary') }} int
      on int.staging_record_id = stg.raw_record_id
    where stg.calendar_date is not null
      and int.calendar_date is distinct from stg.calendar_date::date
),
sleep_diff as (
    select
      'int_health_sleep_summary'::text as model_name,
      'sleep_start_timestamp_gmt'::text as field_name,
      stg.raw_record_id as staging_record_id
    from staging.stg_garmin_sleep_summary stg
    join {{ ref('int_health_sleep_summary') }} int
      on int.staging_record_id = stg.raw_record_id
    where stg.sleep_start_timestamp_gmt is not null
      and int.sleep_start_timestamp_gmt is distinct from to_timestamp(stg.sleep_start_timestamp_gmt::numeric / 1000.0)
),
sleep_metric_diff as (
    select
      'int_health_sleep_metric_detail'::text as model_name,
      'sample_at'::text as field_name,
      stg.raw_record_id as staging_record_id
    from staging.stg_garmin_sleep_metric_detail stg
    join {{ ref('int_health_sleep_metric_detail') }} int
      on int.staging_record_id = stg.raw_record_id
     and int.sample_position = stg.sample_position
     and int.metric_name = stg.metric_name
    where stg.sample_at is not null
      and int.sample_at is distinct from case
        when stg.sample_at ~ '^[0-9]{13}$' then to_timestamp(stg.sample_at::numeric / 1000.0)
        else stg.sample_at::timestamp at time zone 'UTC'
      end
),
heart_rate_sample_diff as (
    select
      'int_health_heart_rate_sample_detail'::text as model_name,
      'sample_timestamp'::text as field_name,
      stg.raw_record_id as staging_record_id
    from staging.stg_garmin_heart_rate_sample_detail stg
    join {{ ref('int_health_heart_rate_sample_detail') }} int
      on int.staging_record_id = stg.raw_record_id
     and int.sample_position = stg.sample_position
    where stg.sample_timestamp is not null
      and int.sample_timestamp is distinct from to_timestamp(stg.sample_timestamp::numeric / 1000.0)
),
medical_report_diff as (
    select
      'int_health_medical_report_snapshot'::text as model_name,
      'report_date'::text as field_name,
      stg.raw_record_id as staging_record_id
    from staging.stg_medical_report_snapshot stg
    join {{ ref('int_health_medical_report_snapshot') }} int
      on int.staging_record_id = stg.raw_record_id
    where stg.report_date is not null
      and int.report_date is distinct from stg.report_date::date
),
medical_report_item_diff as (
    select
      'int_health_medical_report_item_detail'::text as model_name,
      'section_exam_date'::text as field_name,
      stg.raw_record_id as staging_record_id
    from staging.stg_medical_report_item_detail stg
    join {{ ref('int_health_medical_report_item_detail') }} int
      on int.staging_record_id = stg.raw_record_id
     and int.section_position = stg.section_position
     and int.item_position = stg.item_position
    where stg.section_exam_date is not null
      and int.section_exam_date is distinct from stg.section_exam_date::date
)
select * from activity_diff
union all select * from daily_diff
union all select * from sleep_diff
union all select * from sleep_metric_diff
union all select * from heart_rate_sample_diff
union all select * from medical_report_diff
union all select * from medical_report_item_diff

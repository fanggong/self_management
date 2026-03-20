with activity_local_diff as (
    select
      'int_health_activity_detail'::text as model_name,
      'start_time_local'::text as field_name,
      stg.raw_record_id as staging_record_id
    from staging.stg_garmin_activity_detail stg
    join {{ ref('int_health_activity_detail') }} int
      on int.staging_record_id = stg.raw_record_id
    where int.start_time_local is distinct from stg.start_time_local
),
heart_rate_local_diff as (
    select
      'int_health_heart_rate_summary'::text as model_name,
      'start_timestamp_local'::text as field_name,
      stg.raw_record_id as staging_record_id
    from staging.stg_garmin_heart_rate_summary stg
    join {{ ref('int_health_heart_rate_summary') }} int
      on int.staging_record_id = stg.raw_record_id
    where int.start_timestamp_local is distinct from stg.start_timestamp_local
),
medical_item_diff as (
    select
      'int_health_medical_report_item_detail'::text as model_name,
      'result'::text as field_name,
      stg.raw_record_id as staging_record_id
    from staging.stg_medical_report_item_detail stg
    join {{ ref('int_health_medical_report_item_detail') }} int
      on int.staging_record_id = stg.raw_record_id
     and int.section_position = stg.section_position
     and int.item_position = stg.item_position
    where int.section_examiner is distinct from stg.section_examiner
       or int.result is distinct from stg.result
       or int.unit is distinct from stg.unit
       or int.abnormal_flag is distinct from stg.abnormal_flag
)
select * from activity_local_diff
union all select * from heart_rate_local_diff
union all select * from medical_item_diff

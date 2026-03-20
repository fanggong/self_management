with comparisons as (
    select 'int_health_profile_snapshot'::text as intermediate_model, 'stg_garmin_profile_snapshot'::text as staging_model,
           (select count(*) from {{ ref('int_health_profile_snapshot') }}) as intermediate_count,
           (select count(*) from staging.stg_garmin_profile_snapshot) as staging_count
    union all
    select 'int_health_profile_attribute_detail', 'stg_garmin_profile_attribute_detail',
           (select count(*) from {{ ref('int_health_profile_attribute_detail') }}),
           (select count(*) from staging.stg_garmin_profile_attribute_detail)
    union all
    select 'int_health_daily_summary', 'stg_garmin_daily_summary',
           (select count(*) from {{ ref('int_health_daily_summary') }}),
           (select count(*) from staging.stg_garmin_daily_summary)
    union all
    select 'int_health_activity_detail', 'stg_garmin_activity_detail',
           (select count(*) from {{ ref('int_health_activity_detail') }}),
           (select count(*) from staging.stg_garmin_activity_detail)
    union all
    select 'int_health_sleep_summary', 'stg_garmin_sleep_summary',
           (select count(*) from {{ ref('int_health_sleep_summary') }}),
           (select count(*) from staging.stg_garmin_sleep_summary)
    union all
    select 'int_health_sleep_stage_detail', 'stg_garmin_sleep_stage_detail',
           (select count(*) from {{ ref('int_health_sleep_stage_detail') }}),
           (select count(*) from staging.stg_garmin_sleep_stage_detail)
    union all
    select 'int_health_sleep_metric_detail', 'stg_garmin_sleep_metric_detail',
           (select count(*) from {{ ref('int_health_sleep_metric_detail') }}),
           (select count(*) from staging.stg_garmin_sleep_metric_detail)
    union all
    select 'int_health_heart_rate_summary', 'stg_garmin_heart_rate_summary',
           (select count(*) from {{ ref('int_health_heart_rate_summary') }}),
           (select count(*) from staging.stg_garmin_heart_rate_summary)
    union all
    select 'int_health_heart_rate_sample_detail', 'stg_garmin_heart_rate_sample_detail',
           (select count(*) from {{ ref('int_health_heart_rate_sample_detail') }}),
           (select count(*) from staging.stg_garmin_heart_rate_sample_detail)
    union all
    select 'int_health_medical_report_snapshot', 'stg_medical_report_snapshot',
           (select count(*) from {{ ref('int_health_medical_report_snapshot') }}),
           (select count(*) from staging.stg_medical_report_snapshot)
    union all
    select 'int_health_medical_report_item_detail', 'stg_medical_report_item_detail',
           (select count(*) from {{ ref('int_health_medical_report_item_detail') }}),
           (select count(*) from staging.stg_medical_report_item_detail)
)
select *
from comparisons
where intermediate_count <> staging_count

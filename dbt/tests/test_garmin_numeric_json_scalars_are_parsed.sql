with activity_failures as (
    select
      'activity' as model_name,
      'calories' as field_name,
      r.id as raw_record_id
    from {{ source('raw_health', 'health_event_record') }} as r
    join {{ ref('stg_garmin_activity_detail') }} as s
      on s.raw_record_id = r.id
    where r.connector_id = 'garmin-connect'
      and r.source_stream = 'activity'
      and {{ health_json_try_numeric("r.payload_jsonb -> 'calories'") }} is not null
      and s.calories is distinct from {{ health_json_try_numeric("r.payload_jsonb -> 'calories'") }}

    union all

    select
      'activity' as model_name,
      'average_hr' as field_name,
      r.id as raw_record_id
    from {{ source('raw_health', 'health_event_record') }} as r
    join {{ ref('stg_garmin_activity_detail') }} as s
      on s.raw_record_id = r.id
    where r.connector_id = 'garmin-connect'
      and r.source_stream = 'activity'
      and {{ health_json_try_integer("r.payload_jsonb -> 'averageHR'") }} is not null
      and s.average_hr is distinct from {{ health_json_try_integer("r.payload_jsonb -> 'averageHR'") }}
),
daily_summary_failures as (
    select
      'daily_summary' as model_name,
      'total_kilocalories' as field_name,
      r.id as raw_record_id
    from {{ source('raw_health', 'health_snapshot_record') }} as r
    join {{ ref('stg_garmin_daily_summary') }} as s
      on s.raw_record_id = r.id
    where r.connector_id = 'garmin-connect'
      and r.source_stream = 'daily_summary'
      and {{ health_json_try_numeric("r.payload_jsonb -> 'totalKilocalories'") }} is not null
      and s.total_kilocalories is distinct from {{ health_json_try_numeric("r.payload_jsonb -> 'totalKilocalories'") }}

    union all

    select
      'daily_summary' as model_name,
      'average_stress_level' as field_name,
      r.id as raw_record_id
    from {{ source('raw_health', 'health_snapshot_record') }} as r
    join {{ ref('stg_garmin_daily_summary') }} as s
      on s.raw_record_id = r.id
    where r.connector_id = 'garmin-connect'
      and r.source_stream = 'daily_summary'
      and {{ health_json_try_numeric("r.payload_jsonb -> 'averageStressLevel'") }} is not null
      and s.average_stress_level is distinct from {{ health_json_try_numeric("r.payload_jsonb -> 'averageStressLevel'") }}
),
sleep_summary_failures as (
    select
      'sleep_summary' as model_name,
      'avg_sleep_stress' as field_name,
      r.id as raw_record_id
    from {{ source('raw_health', 'health_snapshot_record') }} as r
    join {{ ref('stg_garmin_sleep_summary') }} as s
      on s.raw_record_id = r.id
    where r.connector_id = 'garmin-connect'
      and r.source_stream = 'sleep'
      and {{ health_json_try_numeric("r.payload_jsonb #> '{dailySleepDTO,avgSleepStress}'") }} is not null
      and s.avg_sleep_stress is distinct from {{ health_json_try_numeric("r.payload_jsonb #> '{dailySleepDTO,avgSleepStress}'") }}

    union all

    select
      'sleep_summary' as model_name,
      'average_respiration_value' as field_name,
      r.id as raw_record_id
    from {{ source('raw_health', 'health_snapshot_record') }} as r
    join {{ ref('stg_garmin_sleep_summary') }} as s
      on s.raw_record_id = r.id
    where r.connector_id = 'garmin-connect'
      and r.source_stream = 'sleep'
      and {{ health_json_try_numeric("r.payload_jsonb #> '{dailySleepDTO,averageRespirationValue}'") }} is not null
      and s.average_respiration_value is distinct from {{ health_json_try_numeric("r.payload_jsonb #> '{dailySleepDTO,averageRespirationValue}'") }}
),
profile_failures as (
    select
      'profile_snapshot' as model_name,
      'running_training_speed' as field_name,
      r.id as raw_record_id
    from {{ source('raw_health', 'health_snapshot_record') }} as r
    join {{ ref('stg_garmin_profile_snapshot') }} as s
      on s.raw_record_id = r.id
    where r.connector_id = 'garmin-connect'
      and r.source_stream = 'profile'
      and {{ health_json_try_numeric("r.payload_jsonb -> 'runningTrainingSpeed'") }} is not null
      and s.running_training_speed is distinct from {{ health_json_try_numeric("r.payload_jsonb -> 'runningTrainingSpeed'") }}
),
heart_rate_summary_failures as (
    select
      'heart_rate_summary' as model_name,
      'resting_heart_rate' as field_name,
      r.id as raw_record_id
    from {{ source('raw_health', 'health_timeseries_record') }} as r
    join {{ ref('stg_garmin_heart_rate_summary') }} as s
      on s.raw_record_id = r.id
    where r.connector_id = 'garmin-connect'
      and r.source_stream = 'heart_rate'
      and {{ health_json_try_integer("r.payload_jsonb -> 'restingHeartRate'") }} is not null
      and s.resting_heart_rate is distinct from {{ health_json_try_integer("r.payload_jsonb -> 'restingHeartRate'") }}
)
select *
from activity_failures
union all
select *
from daily_summary_failures
union all
select *
from sleep_summary_failures
union all
select *
from profile_failures
union all
select *
from heart_rate_summary_failures

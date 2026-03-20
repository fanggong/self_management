{{ config(tags=['garmin', 'health', 'staging']) }}

with source_records as (
    select
      id as raw_record_id,
      account_id,
      connector_config_id,
      sync_task_id,
      connector_id,
      source_stream,
      external_id as source_external_id,
      source_record_date,
      source_record_at,
      source_updated_at,
      collected_at,
      payload_hash,
      created_at as raw_created_at,
      updated_at as raw_updated_at,
      payload_jsonb
    from {{ source('raw_health', 'health_timeseries_record') }}
    where connector_id = 'garmin-connect'
      and source_stream = 'heart_rate'
)
select
  raw_record_id,
  account_id,
  connector_config_id,
  sync_task_id,
  connector_id,
  source_stream,
  source_external_id,
  source_record_date,
  source_record_at,
  source_updated_at,
  collected_at,
  payload_hash,
  raw_created_at,
  raw_updated_at,
  {{ health_clean_text("payload_jsonb ->> 'userProfilePK'") }} as user_profile_pk,
  {{ health_clean_text("payload_jsonb ->> 'calendarDate'") }} as calendar_date,
  {{ health_clean_text("payload_jsonb ->> 'startTimestampGMT'") }} as start_timestamp_gmt,
  {{ health_clean_text("payload_jsonb ->> 'endTimestampGMT'") }} as end_timestamp_gmt,
  {{ health_clean_text("payload_jsonb ->> 'startTimestampLocal'") }} as start_timestamp_local,
  {{ health_clean_text("payload_jsonb ->> 'endTimestampLocal'") }} as end_timestamp_local,
  {{ health_json_try_integer("payload_jsonb -> 'restingHeartRate'") }} as resting_heart_rate,
  {{ health_json_try_integer("payload_jsonb -> 'minHeartRate'") }} as min_heart_rate,
  {{ health_json_try_integer("payload_jsonb -> 'maxHeartRate'") }} as max_heart_rate,
  {{ health_json_try_integer("payload_jsonb -> 'lastSevenDaysAvgRestingHeartRate'") }} as last_seven_days_avg_resting_heart_rate

from source_records

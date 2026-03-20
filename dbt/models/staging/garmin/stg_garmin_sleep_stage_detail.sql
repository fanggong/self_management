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
    from {{ source('raw_health', 'health_snapshot_record') }}
    where connector_id = 'garmin-connect'
      and source_stream = 'sleep'
),
stage_rows as (
    select
      source_records.*,
      'sleepLevels' as stage_name,
      stage_item.value as stage_json,
      stage_item.ordinality as stage_position
    from source_records
    left join lateral jsonb_array_elements({{ health_json_array_or_empty("payload_jsonb -> 'sleepLevels'") }})
      with ordinality as stage_item(value, ordinality)
      on true

    union all

    select
      source_records.*,
      'sleepMovement' as stage_name,
      stage_item.value as stage_json,
      stage_item.ordinality as stage_position
    from source_records
    left join lateral jsonb_array_elements({{ health_json_array_or_empty("payload_jsonb -> 'sleepMovement'") }})
      with ordinality as stage_item(value, ordinality)
      on true
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
  {{ health_clean_text("payload_jsonb #>> '{dailySleepDTO,id}'") }} as sleep_id,
  stage_name,
  stage_position,
  {{ health_clean_text("stage_json ->> 'startGMT'") }} as stage_start_at,
  {{ health_clean_text("stage_json ->> 'endGMT'") }} as stage_end_at,
  {{ health_json_try_numeric("stage_json -> 'activityLevel'") }} as activity_level

from stage_rows
where stage_json is not null

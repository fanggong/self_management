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
    from {{ source('raw_health', 'health_event_record') }}
    where connector_id = 'garmin-connect'
      and source_stream = 'activity'
),
role_rows as (
    select
      source_records.*,
      role_item.value as role_json,
      role_item.ordinality as item_position
    from source_records
    left join lateral jsonb_array_elements({{ health_json_array_or_empty("payload_jsonb -> 'userRoles'") }})
      with ordinality as role_item(value, ordinality)
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
  {{ health_clean_text("payload_jsonb ->> 'activityId'") }} as activity_id,
  item_position,
  {{ health_json_scalar_text("role_json") }} as role

from role_rows
where role_json is not null

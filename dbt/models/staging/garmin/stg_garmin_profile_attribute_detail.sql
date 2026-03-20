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
      and source_stream = 'profile'
),
attribute_rows as (
    select
      source_records.*,
      'userRoles' as attribute_name,
      attribute_item.value as item_json,
      attribute_item.ordinality as item_position
    from source_records
    left join lateral jsonb_array_elements({{ health_json_array_or_empty("payload_jsonb -> 'userRoles'") }})
      with ordinality as attribute_item(value, ordinality)
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
  {{ health_clean_text("payload_jsonb ->> 'profileId'") }} as profile_id,
  attribute_name,
  item_position,
  {{ health_json_scalar_text("item_json") }} as item

from attribute_rows
where item_json is not null

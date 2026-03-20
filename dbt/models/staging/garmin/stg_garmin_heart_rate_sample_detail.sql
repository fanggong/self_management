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
),
descriptor_index as (
    select
      source_records.*,
      coalesce(
        (
          select
            {{ health_json_try_integer("descriptor.value -> 'index'") }}
          from jsonb_array_elements({{ health_json_array_or_empty("payload_jsonb -> 'heartRateValueDescriptors'") }}) as descriptor(value)
          where lower(descriptor.value ->> 'key') = 'timestamp'
          limit 1
        ),
        0
      ) as timestamp_index,
      coalesce(
        (
          select
            {{ health_json_try_integer("descriptor.value -> 'index'") }}
          from jsonb_array_elements({{ health_json_array_or_empty("payload_jsonb -> 'heartRateValueDescriptors'") }}) as descriptor(value)
          where lower(descriptor.value ->> 'key') = 'heartrate'
          limit 1
        ),
        1
      ) as heart_rate_index
    from source_records
),
sample_rows as (
    select
      descriptor_index.*,
      sample.value as sample_value,
      sample.ordinality as sample_position
    from descriptor_index
    left join lateral jsonb_array_elements({{ health_json_array_or_empty("payload_jsonb -> 'heartRateValues'") }})
      with ordinality as sample(value, ordinality)
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
  {{ health_clean_text("payload_jsonb ->> 'calendarDate'") }} as calendar_date,
  sample_position,
  {{ health_clean_text("sample_value ->> timestamp_index") }} as sample_timestamp,
  {{ health_json_try_integer("sample_value -> heart_rate_index") }} as heart_rate_value

from sample_rows
where sample_value is not null

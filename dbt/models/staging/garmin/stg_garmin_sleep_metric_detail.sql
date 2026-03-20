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
metric_rows as (
    select
      source_records.*,
      'sleepRestlessMoments' as metric_name,
      metric_item.value as metric_json,
      metric_item.ordinality as sample_position
    from source_records
    left join lateral jsonb_array_elements({{ health_json_array_or_empty("payload_jsonb -> 'sleepRestlessMoments'") }})
      with ordinality as metric_item(value, ordinality)
      on true

    union all

    select
      source_records.*,
      'sleepHeartRate' as metric_name,
      metric_item.value as metric_json,
      metric_item.ordinality as sample_position
    from source_records
    left join lateral jsonb_array_elements({{ health_json_array_or_empty("payload_jsonb -> 'sleepHeartRate'") }})
      with ordinality as metric_item(value, ordinality)
      on true

    union all

    select
      source_records.*,
      'sleepStress' as metric_name,
      metric_item.value as metric_json,
      metric_item.ordinality as sample_position
    from source_records
    left join lateral jsonb_array_elements({{ health_json_array_or_empty("payload_jsonb -> 'sleepStress'") }})
      with ordinality as metric_item(value, ordinality)
      on true

    union all

    select
      source_records.*,
      'sleepBodyBattery' as metric_name,
      metric_item.value as metric_json,
      metric_item.ordinality as sample_position
    from source_records
    left join lateral jsonb_array_elements({{ health_json_array_or_empty("payload_jsonb -> 'sleepBodyBattery'") }})
      with ordinality as metric_item(value, ordinality)
      on true

    union all

    select
      source_records.*,
      'hrvData' as metric_name,
      metric_item.value as metric_json,
      metric_item.ordinality as sample_position
    from source_records
    left join lateral jsonb_array_elements({{ health_json_array_or_empty("payload_jsonb -> 'hrvData'") }})
      with ordinality as metric_item(value, ordinality)
      on true

    union all

    select
      source_records.*,
      'wellnessEpochRespirationDataDTOList' as metric_name,
      metric_item.value as metric_json,
      metric_item.ordinality as sample_position
    from source_records
    left join lateral jsonb_array_elements({{ health_json_array_or_empty("payload_jsonb -> 'wellnessEpochRespirationDataDTOList'") }})
      with ordinality as metric_item(value, ordinality)
      on true

    union all

    select
      source_records.*,
      'wellnessEpochRespirationAverage' as metric_name,
      metric_item.value as metric_json,
      metric_item.ordinality as sample_position
    from source_records
    left join lateral jsonb_array_elements({{ health_json_array_or_empty("payload_jsonb -> 'wellnessEpochRespirationAveragesList'") }})
      with ordinality as metric_item(value, ordinality)
      on true

    union all

    select
      source_records.*,
      'wellnessEpochRespirationHigh' as metric_name,
      metric_item.value as metric_json,
      metric_item.ordinality as sample_position
    from source_records
    left join lateral jsonb_array_elements({{ health_json_array_or_empty("payload_jsonb -> 'wellnessEpochRespirationAveragesList'") }})
      with ordinality as metric_item(value, ordinality)
      on true

    union all

    select
      source_records.*,
      'wellnessEpochRespirationLow' as metric_name,
      metric_item.value as metric_json,
      metric_item.ordinality as sample_position
    from source_records
    left join lateral jsonb_array_elements({{ health_json_array_or_empty("payload_jsonb -> 'wellnessEpochRespirationAveragesList'") }})
      with ordinality as metric_item(value, ordinality)
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
  metric_name,
  sample_position,
  case
    when metric_name = 'wellnessEpochRespirationDataDTOList' then {{ health_clean_text("metric_json ->> 'startTimeGMT'") }}
    when metric_name in ('wellnessEpochRespirationAverage', 'wellnessEpochRespirationHigh', 'wellnessEpochRespirationLow')
      then {{ health_clean_text("metric_json ->> 'epochEndTimestampGmt'") }}
    else {{ health_clean_text("metric_json ->> 'startGMT'") }}
  end as sample_at,
  case
    when metric_name = 'wellnessEpochRespirationDataDTOList' then {{ health_json_try_numeric("metric_json -> 'respirationValue'") }}
    when metric_name = 'wellnessEpochRespirationAverage' then {{ health_json_try_numeric("metric_json -> 'respirationAverageValue'") }}
    when metric_name = 'wellnessEpochRespirationHigh' then {{ health_json_try_numeric("metric_json -> 'respirationHighValue'") }}
    when metric_name = 'wellnessEpochRespirationLow' then {{ health_json_try_numeric("metric_json -> 'respirationLowValue'") }}
    else {{ health_json_try_numeric("metric_json -> 'value'") }}
  end as value

from metric_rows
where metric_json is not null

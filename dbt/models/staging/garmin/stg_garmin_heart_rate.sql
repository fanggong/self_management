{{ config(tags=['garmin', 'health', 'staging']) }}

with source_records as (
    select
      account_id,
      connector_config_id,
      sync_task_id,
      external_id as heart_rate_external_id,
      source_record_date,
      source_record_at,
      source_updated_at,
      collected_at,
      payload_jsonb as heart_rate_payload
    from {{ source('raw_health', 'health_timeseries_record') }}
    where connector_id = 'garmin-connect'
      and source_stream = 'heart_rate'
),
with_descriptor as (
    select
      *,
      coalesce(
        (
          select (descriptor ->> 'index')::integer
          from jsonb_array_elements(coalesce(heart_rate_payload -> 'heartRateValueDescriptors', '[]'::jsonb)) descriptor
          where lower(descriptor ->> 'key') = 'heartrate'
          limit 1
        ),
        1
      ) as heart_rate_index
    from source_records
),
aggregated as (
    select
      account_id,
      connector_config_id,
      sync_task_id,
      heart_rate_external_id,
      source_record_date,
      source_record_at,
      source_updated_at,
      collected_at,
      heart_rate_payload,
      heart_rate_index,
      avg(
        case
          when jsonb_typeof(sample.value) = 'array'
            and (sample.value ->> heart_rate_index) ~ '^-?[0-9]+(\\.[0-9]+)?$'
            then (sample.value ->> heart_rate_index)::numeric
          else null
        end
      ) as derived_average_heart_rate,
      count(*) filter (
        where jsonb_typeof(sample.value) = 'array'
          and (sample.value ->> heart_rate_index) ~ '^-?[0-9]+(\\.[0-9]+)?$'
      ) as derived_sample_count
    from with_descriptor
    left join lateral jsonb_array_elements(coalesce(heart_rate_payload -> 'heartRateValues', '[]'::jsonb)) sample(value)
      on true
    group by
      account_id,
      connector_config_id,
      sync_task_id,
      heart_rate_external_id,
      source_record_date,
      source_record_at,
      source_updated_at,
      collected_at,
      heart_rate_payload,
      heart_rate_index
)
select
  account_id,
  connector_config_id,
  sync_task_id,
  heart_rate_external_id,
  source_record_date,
  source_record_at,
  source_updated_at,
  collected_at,
  nullif(heart_rate_payload ->> 'restingHeartRate', '')::integer as resting_heart_rate,
  nullif(heart_rate_payload ->> 'minHeartRate', '')::integer as min_heart_rate,
  nullif(heart_rate_payload ->> 'maxHeartRate', '')::integer as max_heart_rate,
  coalesce(
    derived_average_heart_rate,
    nullif(heart_rate_payload ->> 'averageHeartRate', '')::numeric
  ) as average_heart_rate,
  coalesce(
    nullif(derived_sample_count, 0),
    nullif(heart_rate_payload ->> 'sampleCount', '')::integer,
    0
  ) as sample_count,
  heart_rate_payload
from aggregated

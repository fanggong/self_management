{{ config(tags=['garmin', 'health', 'staging']) }}

with source_records as (
    select
      account_id,
      connector_config_id,
      sync_task_id,
      external_id as sleep_external_id,
      source_record_date,
      source_record_at,
      source_updated_at,
      collected_at,
      payload_jsonb as sleep_payload
    from {{ source('raw_health', 'health_snapshot_record') }}
    where connector_id = 'garmin-connect'
      and source_stream = 'sleep'
),
normalized as (
    select
      *,
      coalesce(sleep_payload -> 'dailySleepDTO', '{}'::jsonb) as daily_sleep_dto
    from source_records
)
select
  account_id,
  connector_config_id,
  sync_task_id,
  sleep_external_id,
  coalesce(daily_sleep_dto ->> 'id', sleep_payload ->> 'sleepId', sleep_external_id) as sleep_id,
  source_record_date,
  source_updated_at,
  collected_at,
  coalesce(
    case
      when nullif(daily_sleep_dto ->> 'sleepStartTimestampGMT', '') ~ '^[0-9]{13}$'
        then to_timestamp((daily_sleep_dto ->> 'sleepStartTimestampGMT')::numeric / 1000.0)
      when nullif(daily_sleep_dto ->> 'sleepStartTimestampGMT', '') is not null
        then (daily_sleep_dto ->> 'sleepStartTimestampGMT')::timestamptz
      when nullif(sleep_payload ->> 'sleepStartAt', '') is not null
        then (sleep_payload ->> 'sleepStartAt')::timestamptz
      else null
    end,
    source_record_at
  ) as sleep_start_at,
  coalesce(
    case
      when nullif(daily_sleep_dto ->> 'sleepEndTimestampGMT', '') ~ '^[0-9]{13}$'
        then to_timestamp((daily_sleep_dto ->> 'sleepEndTimestampGMT')::numeric / 1000.0)
      when nullif(daily_sleep_dto ->> 'sleepEndTimestampGMT', '') is not null
        then (daily_sleep_dto ->> 'sleepEndTimestampGMT')::timestamptz
      when nullif(sleep_payload ->> 'sleepEndAt', '') is not null
        then (sleep_payload ->> 'sleepEndAt')::timestamptz
      else null
    end,
    source_updated_at
  ) as sleep_end_at,
  coalesce(
    nullif(daily_sleep_dto ->> 'sleepTimeSeconds', '')::integer,
    nullif(sleep_payload ->> 'sleepTimeSeconds', '')::integer
  ) as sleep_time_seconds,
  coalesce(
    nullif(daily_sleep_dto ->> 'napTimeSeconds', '')::integer,
    nullif(sleep_payload ->> 'napTimeSeconds', '')::integer
  ) as nap_time_seconds,
  coalesce(
    nullif(daily_sleep_dto ->> 'deepSleepSeconds', '')::integer,
    nullif(sleep_payload ->> 'deepSleepSeconds', '')::integer
  ) as deep_sleep_seconds,
  coalesce(
    nullif(daily_sleep_dto ->> 'lightSleepSeconds', '')::integer,
    nullif(sleep_payload ->> 'lightSleepSeconds', '')::integer
  ) as light_sleep_seconds,
  coalesce(
    nullif(daily_sleep_dto ->> 'remSleepSeconds', '')::integer,
    nullif(sleep_payload ->> 'remSleepSeconds', '')::integer
  ) as rem_sleep_seconds,
  coalesce(
    nullif(daily_sleep_dto ->> 'awakeSleepSeconds', '')::integer,
    nullif(sleep_payload ->> 'awakeSleepSeconds', '')::integer
  ) as awake_sleep_seconds,
  sleep_payload
from normalized

{{ config(tags=['garmin', 'health', 'staging']) }}

with source_records as (
    select
      account_id,
      connector_config_id,
      sync_task_id,
      external_id as summary_external_id,
      source_record_date,
      source_record_at,
      source_updated_at,
      collected_at,
      payload_jsonb as summary_payload
    from {{ source('raw_health', 'health_snapshot_record') }}
    where connector_id = 'garmin-connect'
      and source_stream = 'daily_summary'
)
select
  account_id,
  connector_config_id,
  sync_task_id,
  summary_external_id,
  source_record_date,
  source_record_at,
  source_updated_at,
  collected_at,
  coalesce(
    nullif(summary_payload ->> 'totalSteps', '')::bigint,
    nullif(summary_payload ->> 'steps', '')::bigint
  ) as steps,
  coalesce(
    nullif(summary_payload ->> 'totalDistanceMeters', '')::numeric(18, 2),
    nullif(summary_payload ->> 'distanceMeters', '')::numeric(18, 2)
  ) as distance_meters,
  coalesce(
    nullif(summary_payload ->> 'totalKilocalories', '')::numeric(18, 2),
    nullif(summary_payload ->> 'caloriesKcal', '')::numeric(18, 2)
  ) as calories_kcal,
  coalesce(
    nullif(summary_payload ->> 'moderateIntensityMinutes', '')::integer,
    nullif(summary_payload ->> 'activeMinutes', '')::integer
  ) as active_minutes,
  summary_payload
from source_records

{{ config(tags=['garmin', 'health', 'staging']) }}

with source_records as (
    select
      account_id,
      connector_config_id,
      sync_task_id,
      external_id as activity_external_id,
      source_record_date,
      source_record_at,
      source_updated_at,
      collected_at,
      payload_jsonb as activity_payload
    from {{ source('raw_health', 'health_event_record') }}
    where connector_id = 'garmin-connect'
      and source_stream = 'activity'
)
select
  account_id,
  connector_config_id,
  sync_task_id,
  activity_external_id,
  coalesce(activity_payload ->> 'activityId', activity_payload ->> 'activityUUID', activity_external_id) as activity_id,
  source_record_date,
  source_updated_at,
  collected_at,
  coalesce(activity_payload ->> 'activityName', activity_payload ->> 'eventType', 'Activity') as activity_name,
  coalesce(
    activity_payload #>> '{activityType,typeKey}',
    activity_payload #>> '{activityType,typeName}',
    activity_payload ->> 'activityType'
  ) as activity_type,
  source_record_at as start_at,
  coalesce(
    nullif(activity_payload ->> 'duration', '')::numeric::integer,
    nullif(activity_payload ->> 'elapsedDuration', '')::numeric::integer,
    nullif(activity_payload ->> 'movingDuration', '')::numeric::integer,
    nullif(activity_payload ->> 'durationSeconds', '')::numeric::integer
  ) as duration_seconds,
  coalesce(
    nullif(activity_payload ->> 'distance', '')::numeric(18, 2),
    nullif(activity_payload ->> 'distanceMeters', '')::numeric(18, 2)
  ) as distance_meters,
  coalesce(
    nullif(activity_payload ->> 'calories', '')::numeric(18, 2),
    nullif(activity_payload ->> 'activeKilocalories', '')::numeric(18, 2),
    nullif(activity_payload ->> 'caloriesKcal', '')::numeric(18, 2)
  ) as calories_kcal,
  coalesce(
    nullif(activity_payload ->> 'averageHR', '')::numeric::integer,
    nullif(activity_payload ->> 'averageHeartRate', '')::numeric::integer
  ) as average_heart_rate,
  coalesce(
    nullif(activity_payload ->> 'maxHR', '')::numeric::integer,
    nullif(activity_payload ->> 'maxHeartRate', '')::numeric::integer
  ) as max_heart_rate,
  nullif(activity_payload ->> 'steps', '')::numeric::integer as steps,
  activity_payload
from source_records

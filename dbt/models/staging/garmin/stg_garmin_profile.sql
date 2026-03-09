{{ config(tags=['garmin', 'health', 'staging']) }}

with source_records as (
    select
      account_id,
      connector_config_id,
      sync_task_id,
      external_id as profile_external_id,
      source_record_date,
      source_record_at,
      source_updated_at,
      collected_at,
      payload_jsonb as profile_payload
    from {{ source('raw_health', 'health_snapshot_record') }}
    where connector_id = 'garmin-connect'
      and source_stream = 'profile'
)
select
  account_id,
  connector_config_id,
  sync_task_id,
  profile_external_id,
  source_record_date,
  source_record_at,
  source_updated_at,
  collected_at,
  coalesce(
    profile_payload ->> 'displayName',
    profile_payload ->> 'fullName',
    profile_payload ->> 'userName',
    profile_external_id
  ) as display_name,
  coalesce(profile_payload ->> 'provider', 'garmin-connect') as provider,
  coalesce(profile_payload ->> 'region', 'cn') as region,
  profile_payload
from source_records

{{ config(tags=['medical_report', 'health', 'staging']) }}

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
    where connector_id = 'medical-report'
      and source_stream = 'medical_report'
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
  {{ health_clean_text("payload_jsonb ->> 'parseSessionId'") }} as parse_session_id,
  {{ health_clean_text("payload_jsonb ->> 'provider'") }} as provider,
  {{ health_clean_text("payload_jsonb ->> 'modelId'") }} as model_id,
  {{ health_clean_text("payload_jsonb ->> 'recordNumber'") }} as record_number,
  {{ health_clean_text("payload_jsonb ->> 'reportDate'") }} as report_date,
  {{ health_clean_text("payload_jsonb ->> 'institution'") }} as institution,
  {{ health_clean_text("payload_jsonb ->> 'fileName'") }} as file_name

from source_records

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
),
section_rows as (
    select
      source_records.*,
      section_item.value as section_json,
      section_item.ordinality as section_position
    from source_records
    left join lateral jsonb_array_elements({{ health_json_array_or_empty("payload_jsonb -> 'sections'") }})
      with ordinality as section_item(value, ordinality)
      on true
),
item_rows as (
    select
      section_rows.*,
      item_item.value as item_json,
      item_item.ordinality as item_position
    from section_rows
    left join lateral jsonb_array_elements({{ health_json_array_or_empty("section_json -> 'items'") }})
      with ordinality as item_item(value, ordinality)
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
  {{ health_clean_text("payload_jsonb ->> 'parseSessionId'") }} as parse_session_id,
  {{ health_clean_text("payload_jsonb ->> 'recordNumber'") }} as record_number,
  {{ health_clean_text("payload_jsonb ->> 'reportDate'") }} as report_date,
  {{ health_clean_text("payload_jsonb ->> 'institution'") }} as institution,
  {{ health_clean_text("payload_jsonb ->> 'fileName'") }} as file_name,
  section_position,
  item_position,
  {{ health_clean_text("section_json ->> 'sectionKey'") }} as section_key,
  {{ health_clean_text("section_json ->> 'examiner'") }} as section_examiner,
  {{ health_clean_text("section_json ->> 'examDate'") }} as section_exam_date,
  {{ health_clean_text("item_json ->> 'itemKey'") }} as item_key,
  {{ health_clean_text("item_json ->> 'result'") }} as result,
  {{ health_clean_text("item_json ->> 'referenceValue'") }} as reference_value,
  {{ health_clean_text("item_json ->> 'unit'") }} as unit,
  {{ health_clean_text("item_json ->> 'abnormalFlag'") }} as abnormal_flag

from item_rows
where section_json is not null
  and item_json is not null

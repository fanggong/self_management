{{ config(tags=['garmin', 'health', 'mart']) }}

select
  account_id,
  connector_config_id,
  activity_external_id,
  activity_id,
  source_record_date,
  activity_name,
  activity_type,
  start_at,
  duration_seconds,
  distance_meters,
  calories_kcal,
  average_heart_rate,
  max_heart_rate,
  steps
from {{ ref('int_activity_enriched') }}

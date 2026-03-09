{{ config(tags=['garmin', 'health', 'intermediate']) }}

select
  account_id,
  connector_config_id,
  heart_rate_external_id,
  source_record_date,
  resting_heart_rate,
  min_heart_rate,
  max_heart_rate,
  average_heart_rate,
  sample_count
from {{ ref('stg_garmin_heart_rate') }}

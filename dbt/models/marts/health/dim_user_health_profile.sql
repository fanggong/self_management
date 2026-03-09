{{ config(tags=['garmin', 'health', 'mart']) }}

with ranked as (
  select
    account_id,
    profile_external_id,
    display_name,
    provider,
    region,
    source_updated_at,
    row_number() over (
      partition by account_id
      order by source_updated_at desc nulls last, source_record_date desc
    ) as rn
  from {{ ref('stg_garmin_profile') }}
)
select
  account_id,
  profile_external_id,
  display_name,
  provider,
  region,
  source_updated_at
from ranked
where rn = 1

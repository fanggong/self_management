{{ config(tags=['intermediate', 'health', 'garmin']) }}

select
  {{ health_intermediate_common_columns('stg') }},
  stg.profile_id,
  stg.attribute_name,
  stg.item_position,
  stg.item
from {{ ref('stg_garmin_profile_attribute_detail') }} as stg

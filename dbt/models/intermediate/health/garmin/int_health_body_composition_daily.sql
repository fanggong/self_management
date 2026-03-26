{{ config(tags=['intermediate', 'health', 'garmin']) }}

select
  {{ health_intermediate_common_columns('stg') }},
  {{ health_try_date('stg.measurement_date') }} as measurement_date,
  {{ health_try_utc_timestamptz('stg.measurement_time_gmt') }} as measurement_time_gmt,
  stg.measurement_time_local,
  stg.weight_kg,
  stg.bmi,
  stg.body_fat_percent,
  stg.body_water_percent,
  stg.muscle_mass_kg,
  stg.bone_mass_kg,
  stg.basal_met_kilocalories,
  stg.active_met_kilocalories,
  stg.visceral_fat_mass_kg,
  stg.visceral_fat_rating,
  stg.physique_rating,
  stg.metabolic_age
from {{ ref('stg_garmin_body_composition') }} as stg

{{ config(tags=['garmin', 'health', 'staging']) }}

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
    where connector_id = 'garmin-connect'
      and source_stream = 'body_composition'
),
parsed as (
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
      coalesce(
        {{ health_clean_text("payload_jsonb #>> '{dateWeightList,0,calendarDate}'") }},
        {{ health_clean_text("payload_jsonb #>> '{dailyWeightSummaries,0,calendarDate}'") }},
        {{ health_clean_text("payload_jsonb #>> '{dateWeightList,0,measurementDate}'") }},
        {{ health_clean_text("payload_jsonb #>> '{dailyWeightSummaries,0,measurementDate}'") }},
        {{ health_clean_text("payload_jsonb #>> '{dateWeightList,0,date}'") }},
        {{ health_clean_text("payload_jsonb #>> '{dailyWeightSummaries,0,date}'") }},
        cast(source_record_date as text)
      ) as measurement_date,
      coalesce(
        {{ health_clean_text("payload_jsonb #>> '{dateWeightList,0,timestampGMT}'") }},
        {{ health_clean_text("payload_jsonb #>> '{dateWeightList,0,measurementTimeGMT}'") }},
        {{ health_clean_text("payload_jsonb #>> '{dateWeightList,0,measurementTimestampGMT}'") }},
        {{ health_clean_text("payload_jsonb #>> '{dailyWeightSummaries,0,timestampGMT}'") }},
        {{ health_clean_text("payload_jsonb #>> '{dailyWeightSummaries,0,measurementTimeGMT}'") }},
        {{ health_clean_text("payload_jsonb #>> '{dailyWeightSummaries,0,measurementTimestampGMT}'") }},
        {{ health_clean_text("payload_jsonb #>> '{dateWeightList,0,samplePk}'") }}
      ) as measurement_time_gmt,
      coalesce(
        {{ health_clean_text("payload_jsonb #>> '{dateWeightList,0,timestampLocal}'") }},
        {{ health_clean_text("payload_jsonb #>> '{dateWeightList,0,measurementTimeLocal}'") }},
        {{ health_clean_text("payload_jsonb #>> '{dateWeightList,0,measurementTimestampLocal}'") }},
        {{ health_clean_text("payload_jsonb #>> '{dailyWeightSummaries,0,timestampLocal}'") }},
        {{ health_clean_text("payload_jsonb #>> '{dailyWeightSummaries,0,measurementTimeLocal}'") }},
        {{ health_clean_text("payload_jsonb #>> '{dailyWeightSummaries,0,measurementTimestampLocal}'") }}
      ) as measurement_time_local,
      coalesce(
        {{ health_json_try_numeric("payload_jsonb #> '{dateWeightList,0,weightInKg}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{dailyWeightSummaries,0,weightInKg}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{totalAverage,weightInKg}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{dateWeightList,0,weight}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{dailyWeightSummaries,0,weight}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{totalAverage,weight}'") }}
      ) as raw_weight,
      coalesce(
        {{ health_json_try_numeric("payload_jsonb #> '{dateWeightList,0,bmi}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{dailyWeightSummaries,0,bmi}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{totalAverage,bmi}'") }}
      ) as bmi,
      coalesce(
        {{ health_json_try_numeric("payload_jsonb #> '{dateWeightList,0,percentFat}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{dateWeightList,0,bodyFat}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{dailyWeightSummaries,0,percentFat}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{dailyWeightSummaries,0,bodyFat}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{totalAverage,percentFat}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{totalAverage,bodyFat}'") }}
      ) as body_fat_percent,
      coalesce(
        {{ health_json_try_numeric("payload_jsonb #> '{dateWeightList,0,percentHydration}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{dateWeightList,0,bodyWater}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{dailyWeightSummaries,0,percentHydration}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{dailyWeightSummaries,0,bodyWater}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{totalAverage,percentHydration}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{totalAverage,bodyWater}'") }}
      ) as body_water_percent,
      coalesce(
        {{ health_json_try_numeric("payload_jsonb #> '{dateWeightList,0,muscleMass}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{dailyWeightSummaries,0,muscleMass}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{totalAverage,muscleMass}'") }}
      ) as raw_muscle_mass,
      coalesce(
        {{ health_json_try_numeric("payload_jsonb #> '{dateWeightList,0,boneMass}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{dailyWeightSummaries,0,boneMass}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{totalAverage,boneMass}'") }}
      ) as raw_bone_mass,
      coalesce(
        {{ health_json_try_numeric("payload_jsonb #> '{dateWeightList,0,basalMet}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{dailyWeightSummaries,0,basalMet}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{totalAverage,basalMet}'") }}
      ) as basal_met_kilocalories,
      coalesce(
        {{ health_json_try_numeric("payload_jsonb #> '{dateWeightList,0,activeMet}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{dailyWeightSummaries,0,activeMet}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{totalAverage,activeMet}'") }}
      ) as active_met_kilocalories,
      coalesce(
        {{ health_json_try_numeric("payload_jsonb #> '{dateWeightList,0,visceralFatMass}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{dailyWeightSummaries,0,visceralFatMass}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{totalAverage,visceralFatMass}'") }}
      ) as raw_visceral_fat_mass,
      coalesce(
        {{ health_json_try_numeric("payload_jsonb #> '{dateWeightList,0,visceralFatRating}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{dateWeightList,0,visceralFat}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{dailyWeightSummaries,0,visceralFatRating}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{dailyWeightSummaries,0,visceralFat}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{totalAverage,visceralFatRating}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{totalAverage,visceralFat}'") }}
      ) as visceral_fat_rating,
      coalesce(
        {{ health_json_try_numeric("payload_jsonb #> '{dateWeightList,0,physiqueRating}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{dailyWeightSummaries,0,physiqueRating}'") }},
        {{ health_json_try_numeric("payload_jsonb #> '{totalAverage,physiqueRating}'") }}
      ) as physique_rating,
      coalesce(
        {{ health_json_try_integer("payload_jsonb #> '{dateWeightList,0,metabolicAge}'") }},
        {{ health_json_try_integer("payload_jsonb #> '{dailyWeightSummaries,0,metabolicAge}'") }},
        {{ health_json_try_integer("payload_jsonb #> '{totalAverage,metabolicAge}'") }}
      ) as metabolic_age
    from source_records
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
  measurement_date,
  measurement_time_gmt,
  measurement_time_local,
  case
    when raw_weight is null then null
    when raw_weight >= 1000 then round(raw_weight / 1000.0, 3)
    else raw_weight
  end as weight_kg,
  bmi,
  body_fat_percent,
  body_water_percent,
  case
    when raw_muscle_mass is null then null
    when raw_muscle_mass >= 1000 then round(raw_muscle_mass / 1000.0, 3)
    else raw_muscle_mass
  end as muscle_mass_kg,
  case
    when raw_bone_mass is null then null
    when raw_bone_mass >= 1000 then round(raw_bone_mass / 1000.0, 3)
    else raw_bone_mass
  end as bone_mass_kg,
  basal_met_kilocalories,
  active_met_kilocalories,
  case
    when raw_visceral_fat_mass is null then null
    when raw_visceral_fat_mass >= 1000 then round(raw_visceral_fat_mass / 1000.0, 3)
    else raw_visceral_fat_mass
  end as visceral_fat_mass_kg,
  visceral_fat_rating,
  physique_rating,
  metabolic_age
from parsed

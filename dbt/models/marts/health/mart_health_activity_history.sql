{{ config(tags=['marts', 'health']) }}

with deduplicated_activity as (
    select
      *,
      row_number() over (
        partition by staging_record_id
        order by coalesce(source_updated_at, source_record_at, start_time_gmt) desc nulls last
      ) as row_number_in_activity
    from {{ ref('int_health_activity_detail') }}
)
select
  staging_record_id as activity_record_id,
  account_id,
  source_external_id,
  source_record_date,
  source_record_at,
  source_updated_at,
  activity_id,
  activity_uuid,
  activity_name,
  coalesce(activity_type_type_key, event_type_type_key) as activity_type,
  start_time_gmt,
  end_time_gmt,
  start_time_local,
  time_zone_id,
  duration as duration_seconds,
  moving_duration as moving_duration_seconds,
  elapsed_duration as elapsed_duration_seconds,
  distance as distance_meters,
  calories as calories_kilocalories,
  bmr_calories as bmr_kilocalories,
  average_hr,
  max_hr,
  average_speed as average_speed_meters_per_second,
  max_speed as max_speed_meters_per_second,
  elevation_gain as elevation_gain_meters,
  elevation_loss as elevation_loss_meters,
  lap_count,
  training_effect_label,
  aerobic_training_effect,
  anaerobic_training_effect,
  hr_time_in_zone_1,
  hr_time_in_zone_2,
  hr_time_in_zone_3,
  hr_time_in_zone_4,
  hr_time_in_zone_5,
  location_name,
  start_latitude,
  start_longitude,
  end_latitude,
  end_longitude,
  manufacturer,
  owner_display_name,
  owner_full_name,
  sport_type_id,
  privacy_type_key
from deduplicated_activity
where row_number_in_activity = 1

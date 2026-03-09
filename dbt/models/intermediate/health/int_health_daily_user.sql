{{ config(tags=['garmin', 'health', 'intermediate']) }}

with daily_summary as (
    select
      account_id,
      connector_config_id,
      source_record_date,
      sum(coalesce(steps, 0)) as steps,
      sum(coalesce(distance_meters, 0)) as distance_meters,
      sum(coalesce(calories_kcal, 0)) as calories_kcal,
      sum(coalesce(active_minutes, 0)) as active_minutes
    from {{ ref('stg_garmin_daily_summary') }}
    group by 1, 2, 3
),
activity_daily as (
    select
      account_id,
      connector_config_id,
      source_record_date,
      count(*) as activity_count,
      sum(coalesce(duration_seconds, 0)) as activity_duration_seconds,
      sum(coalesce(distance_meters, 0)) as activity_distance_meters,
      sum(coalesce(calories_kcal, 0)) as activity_calories_kcal
    from {{ ref('int_activity_enriched') }}
    group by 1, 2, 3
),
sleep_daily as (
    select
      account_id,
      connector_config_id,
      source_record_date,
      sum(coalesce(sleep_time_seconds, 0)) as sleep_time_seconds,
      sum(coalesce(nap_time_seconds, 0)) as nap_time_seconds,
      sum(coalesce(deep_sleep_seconds, 0)) as deep_sleep_seconds,
      sum(coalesce(light_sleep_seconds, 0)) as light_sleep_seconds,
      sum(coalesce(rem_sleep_seconds, 0)) as rem_sleep_seconds,
      sum(coalesce(awake_sleep_seconds, 0)) as awake_sleep_seconds
    from {{ ref('int_sleep_enriched') }}
    group by 1, 2, 3
),
heart_daily as (
    select
      account_id,
      connector_config_id,
      source_record_date,
      max(resting_heart_rate) as resting_heart_rate,
      min(min_heart_rate) as min_heart_rate,
      max(max_heart_rate) as max_heart_rate,
      avg(average_heart_rate) as average_heart_rate,
      sum(coalesce(sample_count, 0)) as heart_rate_sample_count
    from {{ ref('int_heart_rate_daily') }}
    group by 1, 2, 3
),
calendar_spine as (
    select account_id, connector_config_id, source_record_date from daily_summary
    union
    select account_id, connector_config_id, source_record_date from activity_daily
    union
    select account_id, connector_config_id, source_record_date from sleep_daily
    union
    select account_id, connector_config_id, source_record_date from heart_daily
)
select
  spine.account_id,
  spine.connector_config_id,
  spine.source_record_date,
  coalesce(summary.steps, 0) as steps,
  coalesce(summary.distance_meters, 0) as distance_meters,
  coalesce(summary.calories_kcal, 0) as calories_kcal,
  coalesce(summary.active_minutes, 0) as active_minutes,
  coalesce(activity.activity_count, 0) as activity_count,
  coalesce(activity.activity_duration_seconds, 0) as activity_duration_seconds,
  coalesce(activity.activity_distance_meters, 0) as activity_distance_meters,
  coalesce(activity.activity_calories_kcal, 0) as activity_calories_kcal,
  coalesce(sleep.sleep_time_seconds, 0) as sleep_time_seconds,
  coalesce(sleep.nap_time_seconds, 0) as nap_time_seconds,
  coalesce(sleep.deep_sleep_seconds, 0) as deep_sleep_seconds,
  coalesce(sleep.light_sleep_seconds, 0) as light_sleep_seconds,
  coalesce(sleep.rem_sleep_seconds, 0) as rem_sleep_seconds,
  coalesce(sleep.awake_sleep_seconds, 0) as awake_sleep_seconds,
  heart.resting_heart_rate,
  heart.min_heart_rate,
  heart.max_heart_rate,
  round(heart.average_heart_rate, 2) as average_heart_rate,
  coalesce(heart.heart_rate_sample_count, 0) as heart_rate_sample_count
from calendar_spine spine
left join daily_summary summary
  on spine.account_id = summary.account_id
 and spine.connector_config_id = summary.connector_config_id
 and spine.source_record_date = summary.source_record_date
left join activity_daily activity
  on spine.account_id = activity.account_id
 and spine.connector_config_id = activity.connector_config_id
 and spine.source_record_date = activity.source_record_date
left join sleep_daily sleep
  on spine.account_id = sleep.account_id
 and spine.connector_config_id = sleep.connector_config_id
 and spine.source_record_date = sleep.source_record_date
left join heart_daily heart
  on spine.account_id = heart.account_id
 and spine.connector_config_id = heart.connector_config_id
 and spine.source_record_date = heart.source_record_date

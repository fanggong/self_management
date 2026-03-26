{{ config(tags=['marts', 'health']) }}

with latest_daily_summary as (
    select
      *,
      row_number() over (
        partition by account_id, calendar_date
        order by coalesce(source_updated_at, source_record_at) desc nulls last, staging_record_id desc
      ) as row_number_in_metric_date
    from {{ ref('int_health_daily_summary') }}
    where calendar_date is not null
),
latest_body_composition as (
    select
      *,
      row_number() over (
        partition by account_id, measurement_date
        order by coalesce(measurement_time_gmt, source_updated_at, source_record_at) desc nulls last, staging_record_id desc
      ) as row_number_in_measurement_date
    from {{ ref('int_health_body_composition_daily') }}
    where measurement_date is not null
),
latest_profile_snapshot as (
    select
      *,
      row_number() over (
        partition by account_id, source_record_date
        order by coalesce(source_updated_at, source_record_at) desc nulls last, staging_record_id desc
      ) as row_number_in_profile_date
    from {{ ref('int_health_profile_snapshot') }}
    where source_record_date is not null
      and height_cm is not null
),
daily_average_heart_rate as (
    select
      account_id,
      calendar_date,
      round(avg(heart_rate_value)::numeric, 0)::integer as average_heart_rate
    from {{ ref('int_health_heart_rate_sample_detail') }}
    where calendar_date is not null
      and heart_rate_value is not null
    group by 1, 2
)
select
  daily.account_id,
  daily.calendar_date as metric_date,
  daily.max_heart_rate,
  daily.resting_heart_rate,
  heart_rate.average_heart_rate,
  body.weight_kg,
  coalesce(
    body.bmi,
    case
      when body.weight_kg is not null and profile.height_cm is not null and profile.height_cm > 0
        then round((body.weight_kg / power(profile.height_cm / 100.0, 2))::numeric, 1)
      else null
    end
  ) as bmi,
  daily.bmr_kilocalories,
  daily.active_kilocalories,
  daily.average_stress_level,
  daily.low_stress_duration,
  daily.medium_stress_duration,
  daily.high_stress_duration,
  daily.rest_stress_duration
from latest_daily_summary as daily
left join lateral (
    select
      candidate.weight_kg,
      candidate.bmi
    from latest_body_composition as candidate
    where candidate.account_id = daily.account_id
      and candidate.row_number_in_measurement_date = 1
      and candidate.measurement_date = daily.calendar_date
    order by candidate.measurement_date desc, candidate.measurement_time_gmt desc nulls last, candidate.staging_record_id desc
    limit 1
) as body on true
left join daily_average_heart_rate as heart_rate
  on heart_rate.account_id = daily.account_id
 and heart_rate.calendar_date = daily.calendar_date
left join lateral (
    select
      candidate.height_cm
    from latest_profile_snapshot as candidate
    where candidate.account_id = daily.account_id
      and candidate.row_number_in_profile_date = 1
    order by
      case when candidate.source_record_date <= daily.calendar_date then 0 else 1 end,
      case when candidate.source_record_date <= daily.calendar_date then candidate.source_record_date end desc,
      case when candidate.source_record_date > daily.calendar_date then candidate.source_record_date end asc,
      coalesce(candidate.source_updated_at, candidate.source_record_at) desc nulls last,
      candidate.staging_record_id desc
    limit 1
) as profile on true
where daily.row_number_in_metric_date = 1

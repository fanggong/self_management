with _dependency as (
    select 1 from {{ ref('stg_garmin_sleep_stage_detail') }} where false
    union all select 1 from {{ ref('stg_garmin_sleep_metric_detail') }} where false
),
source_row as (
    select
      '{
        "dailySleepDTO": {"id": "sleep-1"},
        "sleepLevels": [
          {"startGMT": "2026-03-17T00:00:00+00:00", "endGMT": "2026-03-17T00:30:00+00:00", "activityLevel": "2"}
        ],
        "sleepMovement": [
          {"startGMT": "2026-03-17T00:30:00+00:00", "endGMT": "2026-03-17T00:35:00+00:00", "activityLevel": "1"}
        ],
        "sleepHeartRate": [
          {"startGMT": "2026-03-17T01:00:00+00:00", "value": "55"},
          {"startGMT": "2026-03-17T01:05:00+00:00", "value": "56"}
        ],
        "wellnessEpochRespirationDataDTOList": [
          {"startTimeGMT": "2026-03-17T01:10:00+00:00", "respirationValue": "14"}
        ],
        "wellnessEpochRespirationAveragesList": [
          {
            "epochEndTimestampGmt": "2026-03-17T01:15:00+00:00",
            "respirationAverageValue": "13",
            "respirationHighValue": "15",
            "respirationLowValue": "11"
          }
        ]
      }'::jsonb as payload_jsonb
),
stage_rows as (
    select
      'sleepLevels'::text as stage_name,
      stage_item.ordinality as stage_position,
      {{ health_clean_text("stage_item.value ->> 'startGMT'") }} as stage_start_at,
      {{ health_clean_text("stage_item.value ->> 'endGMT'") }} as stage_end_at,
      {{ health_json_try_numeric("stage_item.value -> 'activityLevel'") }} as activity_level
    from source_row
    cross join lateral jsonb_array_elements(payload_jsonb -> 'sleepLevels') with ordinality as stage_item(value, ordinality)
    union all
    select
      'sleepMovement'::text as stage_name,
      stage_item.ordinality as stage_position,
      {{ health_clean_text("stage_item.value ->> 'startGMT'") }} as stage_start_at,
      {{ health_clean_text("stage_item.value ->> 'endGMT'") }} as stage_end_at,
      {{ health_json_try_numeric("stage_item.value -> 'activityLevel'") }} as activity_level
    from source_row
    cross join lateral jsonb_array_elements(payload_jsonb -> 'sleepMovement') with ordinality as stage_item(value, ordinality)
),
metric_rows as (
    select
      'sleepHeartRate'::text as metric_name,
      metric_item.ordinality as sample_position,
      {{ health_clean_text("metric_item.value ->> 'startGMT'") }} as sample_at,
      {{ health_try_numeric("metric_item.value ->> 'value'") }} as value
    from source_row
    cross join lateral jsonb_array_elements(payload_jsonb -> 'sleepHeartRate') with ordinality as metric_item(value, ordinality)
    union all
    select
      'wellnessEpochRespirationDataDTOList'::text as metric_name,
      metric_item.ordinality as sample_position,
      {{ health_clean_text("metric_item.value ->> 'startTimeGMT'") }} as sample_at,
      {{ health_try_numeric("metric_item.value ->> 'respirationValue'") }} as value
    from source_row
    cross join lateral jsonb_array_elements(payload_jsonb -> 'wellnessEpochRespirationDataDTOList') with ordinality as metric_item(value, ordinality)
    union all
    select
      'wellnessEpochRespirationAverage'::text as metric_name,
      metric_item.ordinality as sample_position,
      {{ health_clean_text("metric_item.value ->> 'epochEndTimestampGmt'") }} as sample_at,
      {{ health_try_numeric("metric_item.value ->> 'respirationAverageValue'") }} as value
    from source_row
    cross join lateral jsonb_array_elements(payload_jsonb -> 'wellnessEpochRespirationAveragesList') with ordinality as metric_item(value, ordinality)
    union all
    select
      'wellnessEpochRespirationHigh'::text as metric_name,
      metric_item.ordinality as sample_position,
      {{ health_clean_text("metric_item.value ->> 'epochEndTimestampGmt'") }} as sample_at,
      {{ health_try_numeric("metric_item.value ->> 'respirationHighValue'") }} as value
    from source_row
    cross join lateral jsonb_array_elements(payload_jsonb -> 'wellnessEpochRespirationAveragesList') with ordinality as metric_item(value, ordinality)
    union all
    select
      'wellnessEpochRespirationLow'::text as metric_name,
      metric_item.ordinality as sample_position,
      {{ health_clean_text("metric_item.value ->> 'epochEndTimestampGmt'") }} as sample_at,
      {{ health_try_numeric("metric_item.value ->> 'respirationLowValue'") }} as value
    from source_row
    cross join lateral jsonb_array_elements(payload_jsonb -> 'wellnessEpochRespirationAveragesList') with ordinality as metric_item(value, ordinality)
),
expected_stage_rows as (
    select
      'sleepLevels'::text as stage_name,
      1::bigint as stage_position,
      '2026-03-17T00:00:00+00:00'::text as stage_start_at,
      '2026-03-17T00:30:00+00:00'::text as stage_end_at,
      2::numeric as activity_level
    union all
    select
      'sleepMovement'::text,
      1::bigint,
      '2026-03-17T00:30:00+00:00'::text,
      '2026-03-17T00:35:00+00:00'::text,
      1::numeric
),
expected_metric_rows as (
    select 'sleepHeartRate'::text as metric_name, 1::bigint as sample_position, '2026-03-17T01:00:00+00:00'::text as sample_at, 55::numeric as value
    union all
    select 'sleepHeartRate'::text, 2::bigint, '2026-03-17T01:05:00+00:00'::text, 56::numeric
    union all
    select 'wellnessEpochRespirationDataDTOList'::text, 1::bigint, '2026-03-17T01:10:00+00:00'::text, 14::numeric
    union all
    select 'wellnessEpochRespirationAverage'::text, 1::bigint, '2026-03-17T01:15:00+00:00'::text, 13::numeric
    union all
    select 'wellnessEpochRespirationHigh'::text, 1::bigint, '2026-03-17T01:15:00+00:00'::text, 15::numeric
    union all
    select 'wellnessEpochRespirationLow'::text, 1::bigint, '2026-03-17T01:15:00+00:00'::text, 11::numeric
),
stage_diff as (
    (select * from stage_rows except select * from expected_stage_rows)
    union all
    (select * from expected_stage_rows except select * from stage_rows)
),
metric_diff as (
    (select * from metric_rows except select * from expected_metric_rows)
    union all
    (select * from expected_metric_rows except select * from metric_rows)
)
select
  'stage'::text as diff_type,
  stage_name as name_1,
  stage_position::text as name_2,
  stage_start_at::text as value_1,
  stage_end_at::text as value_2,
  activity_level::text as value_3
from stage_diff
union all
select
  'metric'::text as diff_type,
  metric_name as name_1,
  sample_position::text as name_2,
  sample_at::text as value_1,
  value::text as value_2,
  null::text as value_3
from metric_diff

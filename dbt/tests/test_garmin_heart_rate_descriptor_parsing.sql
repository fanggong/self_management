with _dependency as (
    select 1 from {{ ref('stg_garmin_heart_rate_sample_detail') }} limit 0
),
source_row as (
    select
      '[{"index":0,"key":"timestamp"},{"index":1,"key":"heartrate"}]'::jsonb as descriptors,
      '[[1710000000000,68],[1710000300000,"70"]]'::jsonb as values
),
descriptor_index as (
    select
      coalesce(
        (
          select {{ health_try_integer("descriptor.value ->> 'index'") }}
          from source_row
          cross join lateral jsonb_array_elements(descriptors) as descriptor(value)
          where lower(descriptor.value ->> 'key') = 'timestamp'
          limit 1
        ),
        0
      ) as timestamp_index,
      coalesce(
        (
          select {{ health_try_integer("descriptor.value ->> 'index'") }}
          from source_row
          cross join lateral jsonb_array_elements(descriptors) as descriptor(value)
          where lower(descriptor.value ->> 'key') = 'heartrate'
          limit 1
        ),
        1
      ) as heart_rate_index
    from source_row
),
decoded as (
    select
      sample.ordinality as sample_position,
      {{ health_clean_text("sample.value ->> descriptor_index.timestamp_index") }} as sample_timestamp,
      {{ health_try_integer("sample.value ->> descriptor_index.heart_rate_index") }} as heart_rate_value
    from source_row
    cross join descriptor_index
    cross join lateral jsonb_array_elements(values) with ordinality as sample(value, ordinality)
),
expected as (
    select 1::bigint as sample_position, '1710000000000'::text as sample_timestamp, 68::integer as heart_rate_value
    union all
    select 2::bigint as sample_position, '1710000300000'::text as sample_timestamp, 70::integer as heart_rate_value
),
diff as (
    (select * from decoded except select * from expected)
    union all
    (select * from expected except select * from decoded)
)
select *
from diff

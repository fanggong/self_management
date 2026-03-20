with _dependency as (
    select 1 from {{ ref('int_health_medical_report_item_detail') }} limit 0
),
source_values as (
    select '4.9-5.3'::text as raw_value, 4.9::numeric as expected_min, 5.3::numeric as expected_max
    union all
    select '4.30--5.80'::text, 4.30::numeric, 5.80::numeric
    union all
    select '0--1'::text, 0::numeric, 1::numeric
    union all
    select ''::text, null::numeric, null::numeric
    union all
    select '>=5'::text, null::numeric, null::numeric
    union all
    select 'normal'::text, null::numeric, null::numeric
    union all
    select null::text, null::numeric, null::numeric
),
parsed as (
    select
      raw_value,
      {{ health_try_numeric_range_min('raw_value') }} as reference_value_min,
      {{ health_try_numeric_range_max('raw_value') }} as reference_value_max
    from source_values
),
expected as (
    select
      raw_value,
      expected_min as reference_value_min,
      expected_max as reference_value_max
    from source_values
),
diff as (
    (select * from parsed except select * from expected)
    union all
    (select * from expected except select * from parsed)
)
select *
from diff

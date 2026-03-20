with _dependency as (
    select 1 from {{ ref('stg_garmin_daily_summary') }} limit 0
),
typed as (
    select
      {{ health_try_numeric("'12.5'") }} as numeric_value,
      {{ health_try_numeric("'627.0'") }} as whole_number_decimal_numeric_value,
      {{ health_json_try_numeric("to_jsonb(627.0::numeric)") }} as json_numeric_value,
      {{ health_try_numeric("'bad-number'") }} as invalid_numeric_value,
      {{ health_try_boolean("'TRUE'") }} as boolean_value,
      {{ health_json_try_boolean("to_jsonb(true)") }} as json_boolean_value,
      {{ health_try_integer("'152.0'") }} as integer_from_decimal_value,
      {{ health_json_try_integer("to_jsonb(152.0::numeric)") }} as json_integer_from_decimal_value,
      {{ health_try_bigint("'1234567890123.0'") }} as bigint_from_decimal_value,
      {{ health_json_try_bigint("to_jsonb(1234567890123.0::numeric)") }} as json_bigint_from_decimal_value,
      {{ health_clean_text("'not-a-ts'") }} as timestamp_value
)
select *
from typed
where not (
  numeric_value = 12.5
  and whole_number_decimal_numeric_value = 627.0
  and json_numeric_value = 627.0
  and invalid_numeric_value is null
  and boolean_value = true
  and json_boolean_value = true
  and integer_from_decimal_value = 152
  and json_integer_from_decimal_value = 152
  and bigint_from_decimal_value = 1234567890123
  and json_bigint_from_decimal_value = 1234567890123
  and timestamp_value = 'not-a-ts'
)

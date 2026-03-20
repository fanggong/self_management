{% macro health_clean_text(sql_expression) -%}
nullif(btrim(cast({{ sql_expression }} as text)), '')
{%- endmacro %}

{% macro health_json_scalar_text(sql_expression) -%}
nullif(trim(both '"' from cast({{ sql_expression }} as text)), '')
{%- endmacro %}

{% macro health_json_array_or_empty(sql_expression) -%}
case
  when jsonb_typeof({{ sql_expression }}) = 'array' then {{ sql_expression }}
  else '[]'::jsonb
end
{%- endmacro %}

{% macro health_json_object_or_empty(sql_expression) -%}
case
  when jsonb_typeof({{ sql_expression }}) = 'object' then {{ sql_expression }}
  else '{}'::jsonb
end
{%- endmacro %}

{% macro health_try_integer(sql_expression) -%}
case
  when {{ health_clean_text(sql_expression) }} ~ '^-?(?:[0-9]+(?:[.][0-9]+)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?$'
    and mod(({{ health_clean_text(sql_expression) }})::numeric, 1) = 0
    and ({{ health_clean_text(sql_expression) }})::numeric between -2147483648 and 2147483647
    then ({{ health_clean_text(sql_expression) }})::numeric::integer
  else null
end
{%- endmacro %}

{% macro health_try_bigint(sql_expression) -%}
case
  when {{ health_clean_text(sql_expression) }} ~ '^-?(?:[0-9]+(?:[.][0-9]+)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?$'
    and mod(({{ health_clean_text(sql_expression) }})::numeric, 1) = 0
    and ({{ health_clean_text(sql_expression) }})::numeric between -9223372036854775808 and 9223372036854775807
    then ({{ health_clean_text(sql_expression) }})::numeric::bigint
  else null
end
{%- endmacro %}

{% macro health_try_numeric(sql_expression) -%}
case
  when {{ health_clean_text(sql_expression) }} ~ '^-?(?:[0-9]+(?:[.][0-9]+)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?$'
    then ({{ health_clean_text(sql_expression) }})::numeric
  else null
end
{%- endmacro %}

{% macro health_try_numeric_range_bound(sql_expression, bound_index) -%}
case
  when {{ health_clean_text(sql_expression) }} ~ '^((?:[0-9]+(?:[.][0-9]+)?|[.][0-9]+))[[:space:]]*(?:--|-)[[:space:]]*((?:[0-9]+(?:[.][0-9]+)?|[.][0-9]+))$'
    then (regexp_match({{ health_clean_text(sql_expression) }}, '^((?:[0-9]+(?:[.][0-9]+)?|[.][0-9]+))[[:space:]]*(?:--|-)[[:space:]]*((?:[0-9]+(?:[.][0-9]+)?|[.][0-9]+))$'))[{{ bound_index }}]::numeric
  else null
end
{%- endmacro %}

{% macro health_try_numeric_range_min(sql_expression) -%}
{{ health_try_numeric_range_bound(sql_expression, 1) }}
{%- endmacro %}

{% macro health_try_numeric_range_max(sql_expression) -%}
{{ health_try_numeric_range_bound(sql_expression, 2) }}
{%- endmacro %}

{% macro health_try_boolean(sql_expression) -%}
case
  when lower({{ health_clean_text(sql_expression) }}) in ('true', 't', '1', 'yes', 'y') then true
  when lower({{ health_clean_text(sql_expression) }}) in ('false', 'f', '0', 'no', 'n') then false
  else null
end
{%- endmacro %}

{% macro health_json_try_integer(sql_expression) -%}
public.otw_health_json_try_integer({{ sql_expression }})
{%- endmacro %}

{% macro health_json_try_bigint(sql_expression) -%}
public.otw_health_json_try_bigint({{ sql_expression }})
{%- endmacro %}

{% macro health_json_try_numeric(sql_expression) -%}
public.otw_health_json_try_numeric({{ sql_expression }})
{%- endmacro %}

{% macro health_json_try_boolean(sql_expression) -%}
public.otw_health_json_try_boolean({{ sql_expression }})
{%- endmacro %}

{% macro create_health_parsing_functions() %}
  {% if execute %}
    {% set create_scalar_text %}
      create or replace function public.otw_health_json_scalar_text(value jsonb)
      returns text
      language sql
      immutable
      strict
      as $$
        select nullif(trim(both '"' from cast(value as text)), '')
      $$;
    {% endset %}
    {% do run_query(create_scalar_text) %}

    {% set create_try_numeric %}
      create or replace function public.otw_health_json_try_numeric(value jsonb)
      returns numeric
      language sql
      immutable
      strict
      as $$
        select case
          when public.otw_health_json_scalar_text(value) ~ '^-?(?:[0-9]+(?:[.][0-9]+)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?$'
            then public.otw_health_json_scalar_text(value)::numeric
          else null
        end
      $$;
    {% endset %}
    {% do run_query(create_try_numeric) %}

    {% set create_try_integer %}
      create or replace function public.otw_health_json_try_integer(value jsonb)
      returns integer
      language sql
      immutable
      strict
      as $$
        select case
          when public.otw_health_json_scalar_text(value) ~ '^-?(?:[0-9]+(?:[.][0-9]+)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?$'
            and mod(public.otw_health_json_scalar_text(value)::numeric, 1) = 0
            and public.otw_health_json_scalar_text(value)::numeric between -2147483648 and 2147483647
            then public.otw_health_json_scalar_text(value)::numeric::integer
          else null
        end
      $$;
    {% endset %}
    {% do run_query(create_try_integer) %}

    {% set create_try_bigint %}
      create or replace function public.otw_health_json_try_bigint(value jsonb)
      returns bigint
      language sql
      immutable
      strict
      as $$
        select case
          when public.otw_health_json_scalar_text(value) ~ '^-?(?:[0-9]+(?:[.][0-9]+)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?$'
            and mod(public.otw_health_json_scalar_text(value)::numeric, 1) = 0
            and public.otw_health_json_scalar_text(value)::numeric between -9223372036854775808 and 9223372036854775807
            then public.otw_health_json_scalar_text(value)::numeric::bigint
          else null
        end
      $$;
    {% endset %}
    {% do run_query(create_try_bigint) %}

    {% set create_try_boolean %}
      create or replace function public.otw_health_json_try_boolean(value jsonb)
      returns boolean
      language sql
      immutable
      strict
      as $$
        select case
          when lower(public.otw_health_json_scalar_text(value)) in ('true', 't', '1', 'yes', 'y') then true
          when lower(public.otw_health_json_scalar_text(value)) in ('false', 'f', '0', 'no', 'n') then false
          else null
        end
      $$;
    {% endset %}
    {% do run_query(create_try_boolean) %}

    {% set create_leaf_paths %}
      create or replace function public.otw_health_json_leaf_paths(value jsonb)
      returns table(path text)
      language sql
      immutable
      as $$
        with recursive walk(path_parts, node) as (
          select array[]::text[], value
          union all
          select
            walk.path_parts || child.key,
            child.value
          from walk
          cross join lateral (
            select obj.key, obj.value
            from jsonb_each(walk.node) as obj(key, value)
            where jsonb_typeof(walk.node) = 'object'

            union all

            select '[]'::text as key, arr.value
            from jsonb_array_elements(walk.node) as arr(value)
            where jsonb_typeof(walk.node) = 'array'
          ) as child
        )
        select replace(array_to_string(path_parts, '.'), '.[]', '[]') as path
        from walk
        where jsonb_typeof(node) not in ('object', 'array')
      $$;
    {% endset %}
    {% do run_query(create_leaf_paths) %}
  {% endif %}
{% endmacro %}

{% macro health_try_date(sql_expression) -%}
case
  when {{ health_clean_text(sql_expression) }} ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
    then ({{ health_clean_text(sql_expression) }})::date
  when {{ health_clean_text(sql_expression) }} ~ '^[0-9]{13}$'
    then to_timestamp(({{ health_clean_text(sql_expression) }})::numeric / 1000.0)::date
  when {{ health_clean_text(sql_expression) }} ~ '^[0-9]{10}$'
    then to_timestamp(({{ health_clean_text(sql_expression) }})::numeric)::date
  when {{ health_clean_text(sql_expression) }} ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}[T ].*$'
    then ({{ health_clean_text(sql_expression) }})::timestamptz::date
  else null
end
{%- endmacro %}

{% macro health_try_timestamptz(sql_expression) -%}
case
  when {{ health_clean_text(sql_expression) }} ~ '^[0-9]{13}$'
    then to_timestamp(({{ health_clean_text(sql_expression) }})::numeric / 1000.0)
  when {{ health_clean_text(sql_expression) }} ~ '^[0-9]{10}$'
    then to_timestamp(({{ health_clean_text(sql_expression) }})::numeric)
  when {{ health_clean_text(sql_expression) }} ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}[T ].*$'
    then ({{ health_clean_text(sql_expression) }})::timestamptz
  else null
end
{%- endmacro %}

{% macro health_try_utc_timestamptz(sql_expression) -%}
case
  when {{ health_clean_text(sql_expression) }} ~ '^[0-9]{13}$'
    then to_timestamp(({{ health_clean_text(sql_expression) }})::numeric / 1000.0)
  when {{ health_clean_text(sql_expression) }} ~ '^[0-9]{10}$'
    then to_timestamp(({{ health_clean_text(sql_expression) }})::numeric)
  when {{ health_clean_text(sql_expression) }} ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}[T ][0-9]{2}:[0-9]{2}:[0-9]{2}([.][0-9]+)?(Z|[+-][0-9]{2}(:?[0-9]{2})?)?$'
    then case
      when {{ health_clean_text(sql_expression) }} ~ '(Z|[+-][0-9]{2}(:?[0-9]{2})?)$'
        then ({{ health_clean_text(sql_expression) }})::timestamptz
      else ({{ health_clean_text(sql_expression) }})::timestamp at time zone 'UTC'
    end
  else null
end
{%- endmacro %}

{% macro health_try_numeric_range_lower(sql_expression) -%}
case
  when {{ health_clean_text(sql_expression) }} ~ '^[[:space:]]*-?(?:[0-9]+(?:[.][0-9]+)?|[.][0-9]+)[[:space:]]*[-~～][[:space:]]*-?(?:[0-9]+(?:[.][0-9]+)?|[.][0-9]+)[[:space:]]*$'
    then (regexp_match({{ health_clean_text(sql_expression) }}, '^[[:space:]]*(-?(?:[0-9]+(?:[.][0-9]+)?|[.][0-9]+))[[:space:]]*[-~～][[:space:]]*(-?(?:[0-9]+(?:[.][0-9]+)?|[.][0-9]+))[[:space:]]*$'))[1]::numeric
  else null
end
{%- endmacro %}

{% macro health_try_numeric_range_upper(sql_expression) -%}
case
  when {{ health_clean_text(sql_expression) }} ~ '^[[:space:]]*-?(?:[0-9]+(?:[.][0-9]+)?|[.][0-9]+)[[:space:]]*[-~～][[:space:]]*-?(?:[0-9]+(?:[.][0-9]+)?|[.][0-9]+)[[:space:]]*$'
    then (regexp_match({{ health_clean_text(sql_expression) }}, '^[[:space:]]*(-?(?:[0-9]+(?:[.][0-9]+)?|[.][0-9]+))[[:space:]]*[-~～][[:space:]]*(-?(?:[0-9]+(?:[.][0-9]+)?|[.][0-9]+))[[:space:]]*$'))[2]::numeric
  else null
end
{%- endmacro %}

{% macro health_intermediate_common_columns(alias='') -%}
{%- set prefix = alias ~ '.' if alias else '' -%}
{{ prefix }}raw_record_id as staging_record_id,
{{ prefix }}account_id,
{{ prefix }}source_external_id,
{{ prefix }}source_record_date,
{{ prefix }}source_record_at,
{{ prefix }}source_updated_at
{%- endmacro %}

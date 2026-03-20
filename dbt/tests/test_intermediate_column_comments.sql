{% set issues = [] %}

{% for node in graph.nodes.values() %}
  {% if node.resource_type == 'model' and node.package_name == 'otw' and node.original_file_path.startswith('models/intermediate/') %}
    {% for column_name, column in node.columns.items() %}
      {% do issues.append(
        "select '" ~ node.name ~ "'::text as model_name, '" ~ column_name ~ "'::text as column_name "
        ~ "where not exists ("
        ~ "select 1 "
        ~ "from pg_class c "
        ~ "join pg_namespace n on n.oid = c.relnamespace "
        ~ "join pg_attribute a on a.attrelid = c.oid and a.attname = '" ~ column_name ~ "' and a.attnum > 0 and not a.attisdropped "
        ~ "left join pg_description d on d.objoid = c.oid and d.objsubid = a.attnum "
        ~ "where n.nspname = 'intermediate' and c.relname = '" ~ node.name ~ "' "
        ~ "and d.description is not null and btrim(d.description) <> ''"
        ~ ")"
      ) %}
    {% endfor %}
  {% endif %}
{% endfor %}

with _dependency as (
    select 1 from {{ ref('int_health_profile_snapshot') }} where false
    union all select 1 from {{ ref('int_health_profile_attribute_detail') }} where false
    union all select 1 from {{ ref('int_health_daily_summary') }} where false
    union all select 1 from {{ ref('int_health_activity_detail') }} where false
    union all select 1 from {{ ref('int_health_sleep_summary') }} where false
    union all select 1 from {{ ref('int_health_sleep_stage_detail') }} where false
    union all select 1 from {{ ref('int_health_sleep_metric_detail') }} where false
    union all select 1 from {{ ref('int_health_heart_rate_summary') }} where false
    union all select 1 from {{ ref('int_health_heart_rate_sample_detail') }} where false
    union all select 1 from {{ ref('int_health_medical_report_snapshot') }} where false
    union all select 1 from {{ ref('int_health_medical_report_item_detail') }} where false
),
issues as (
    {% if issues %}
      {{ issues | join('\n      union all\n      ') }}
    {% else %}
      select null::text as model_name, null::text as column_name where false
    {% endif %}
)
select *
from issues

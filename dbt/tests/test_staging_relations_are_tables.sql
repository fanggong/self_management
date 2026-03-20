{% set staging_models = [] %}

{% for node in graph.nodes.values() %}
  {% if node.resource_type == 'model' and node.package_name == 'otw' and node.original_file_path.startswith('models/staging/') %}
    {% do staging_models.append(node.name) %}
  {% endif %}
{% endfor %}

with _dependency as (
    select 1 from {{ ref('stg_garmin_profile_snapshot') }} where false
    union all select 1 from {{ ref('stg_garmin_profile_attribute_detail') }} where false
    union all select 1 from {{ ref('stg_garmin_daily_summary') }} where false
    union all select 1 from {{ ref('stg_garmin_activity_detail') }} where false
    union all select 1 from {{ ref('stg_garmin_activity_role_detail') }} where false
    union all select 1 from {{ ref('stg_garmin_sleep_summary') }} where false
    union all select 1 from {{ ref('stg_garmin_sleep_stage_detail') }} where false
    union all select 1 from {{ ref('stg_garmin_sleep_metric_detail') }} where false
    union all select 1 from {{ ref('stg_garmin_heart_rate_summary') }} where false
    union all select 1 from {{ ref('stg_garmin_heart_rate_sample_detail') }} where false
    union all select 1 from {{ ref('stg_medical_report_snapshot') }} where false
    union all select 1 from {{ ref('stg_medical_report_item_detail') }} where false
),
expected_models as (
    {% for model_name in staging_models %}
      select '{{ model_name }}'::text as model_name{% if not loop.last %} union all{% endif %}
    {% endfor %}
),
relation_state as (
    select
      expected_models.model_name,
      pg_class.relkind
    from expected_models
    left join pg_namespace
      on pg_namespace.nspname = 'staging'
    left join pg_class
      on pg_class.relnamespace = pg_namespace.oid
     and pg_class.relname = expected_models.model_name
)
select
  model_name,
  relkind
from relation_state
where relkind is distinct from 'r'
  and relkind is distinct from 'p'

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
)
select
  table_name as model_name,
  column_name
from information_schema.columns
where table_schema = 'staging'
  and table_name in (
    {% for model_name in staging_models %}
      '{{ model_name }}'{% if not loop.last %}, {% endif %}
    {% endfor %}
  )
  and column_name ~ '_raw$'

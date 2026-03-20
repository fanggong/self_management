{% set expected_models = [
  'stg_garmin_profile_snapshot',
  'stg_garmin_profile_attribute_detail',
  'stg_garmin_daily_summary',
  'stg_garmin_activity_detail',
  'stg_garmin_activity_role_detail',
  'stg_garmin_sleep_summary',
  'stg_garmin_sleep_stage_detail',
  'stg_garmin_sleep_metric_detail',
  'stg_garmin_heart_rate_summary',
  'stg_garmin_heart_rate_sample_detail',
  'stg_medical_report_snapshot',
  'stg_medical_report_item_detail'
] %}
{% set actual_models = [] %}
{% set issues = [] %}

{% for node in graph.nodes.values() %}
  {% if node.resource_type == 'model' and node.package_name == 'otw' and node.original_file_path.startswith('models/staging/') %}
    {% do actual_models.append(node.name) %}
  {% endif %}
{% endfor %}

{% for model_name in actual_models %}
  {% if model_name not in expected_models %}
    {% do issues.append("select '" ~ model_name ~ "'::text as model_name, 'unexpected_model'::text as issue") %}
  {% endif %}
{% endfor %}

{% for model_name in expected_models %}
  {% if model_name not in actual_models %}
    {% do issues.append("select '" ~ model_name ~ "'::text as model_name, 'missing_model'::text as issue") %}
  {% endif %}
{% endfor %}

with _dependency as (
    select 1 from {{ ref('stg_garmin_profile_snapshot') }} limit 0
),
issues as (
    {% if issues %}
      {{ issues | join('\n      union all\n      ') }}
    {% else %}
      select null::text as model_name, null::text as issue where false
    {% endif %}
)
select *
from issues

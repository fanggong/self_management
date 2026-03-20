{% set issues = [] %}

{% for node in graph.nodes.values() %}
  {% if node.resource_type == 'model' and node.package_name == 'otw' and node.original_file_path.startswith('models/intermediate/') %}
    {% if not node.description or not (node.description | trim) %}
      {% do issues.append("select '" ~ node.name ~ "'::text as model_name, null::text as column_name, 'missing_model_description'::text as issue") %}
    {% endif %}
    {% for column_name, column in node.columns.items() %}
      {% if not column.description or not (column.description | trim) %}
        {% do issues.append("select '" ~ node.name ~ "'::text as model_name, '" ~ column_name ~ "'::text as column_name, 'missing_column_description'::text as issue") %}
      {% endif %}
      {% if not column.data_type or not (column.data_type | trim) %}
        {% do issues.append("select '" ~ node.name ~ "'::text as model_name, '" ~ column_name ~ "'::text as column_name, 'missing_column_data_type'::text as issue") %}
      {% endif %}
    {% endfor %}
  {% endif %}
{% endfor %}

with _dependency as (
    select 1 from {{ ref('int_health_profile_snapshot') }} limit 0
),
issues as (
    {% if issues %}
      {{ issues | join('\n      union all\n      ') }}
    {% else %}
      select null::text as model_name, null::text as column_name, null::text as issue where false
    {% endif %}
)
select *
from issues

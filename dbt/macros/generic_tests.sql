{% test unique_combination(model, column_names) %}
select
  {% for column_name in column_names %}
    {{ column_name }}{% if not loop.last %}, {% endif %}
  {% endfor %},
  count(*) as row_count
from {{ model }}
group by
  {% for column_name in column_names %}
    {{ column_name }}{% if not loop.last %}, {% endif %}
  {% endfor %}
having count(*) > 1
{% endtest %}

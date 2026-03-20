{% set staging_models = [] %}

{% for node in graph.nodes.values() %}
  {% if node.resource_type == 'model' and node.package_name == 'otw' and node.original_file_path.startswith('models/staging/') %}
    {% do staging_models.append(node.name) %}
  {% endif %}
{% endfor %}

with _dependency as (
    select 1 from {{ ref('staging_lineage_contract') }} where false
    union all select 1 from {{ ref('stg_garmin_profile_snapshot') }} where false
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
raw_records as (
    select connector_id, source_stream, payload_jsonb
    from {{ source('raw_health', 'health_snapshot_record') }}
    union all
    select connector_id, source_stream, payload_jsonb
    from {{ source('raw_health', 'health_event_record') }}
    union all
    select connector_id, source_stream, payload_jsonb
    from {{ source('raw_health', 'health_timeseries_record') }}
),
actual_leaf_paths as (
    select
      raw_records.connector_id,
      raw_records.source_stream,
      leaf.path as raw_path
    from raw_records
    cross join lateral public.otw_health_json_leaf_paths(raw_records.payload_jsonb) as leaf(path)
    group by 1, 2, 3
),
effective_leaf_paths as (
    select actual_leaf_paths.*
    from actual_leaf_paths
    where not exists (
      select 1
      from actual_leaf_paths as descendant_path
      where descendant_path.connector_id = actual_leaf_paths.connector_id
        and descendant_path.source_stream = actual_leaf_paths.source_stream
        and (
          descendant_path.raw_path like actual_leaf_paths.raw_path || '.%'
          or descendant_path.raw_path like actual_leaf_paths.raw_path || '[]%'
        )
    )
),
staging_columns as (
    select
      table_name as staging_model,
      column_name as staging_column
    from information_schema.columns
    where table_schema = 'staging'
      and table_name in (
        {% for model_name in staging_models | sort %}
          '{{ model_name }}'{% if not loop.last %}, {% endif %}
        {% endfor %}
      )
),
contract_columns as (
    select
      staging_model,
      staging_column
    from {{ ref('staging_lineage_contract') }}
    group by 1, 2
),
missing_contract_rows as (
    select
      'missing_contract'::text as issue_type,
      staging_columns.staging_model,
      staging_columns.staging_column,
      null::text as raw_path
    from staging_columns
    left join contract_columns
      on contract_columns.staging_model = staging_columns.staging_model
     and contract_columns.staging_column = staging_columns.staging_column
    where contract_columns.staging_column is null
),
orphan_contract_rows as (
    select
      'orphan_contract'::text as issue_type,
      contract_columns.staging_model,
      contract_columns.staging_column,
      null::text as raw_path
    from contract_columns
    left join staging_columns
      on staging_columns.staging_model = contract_columns.staging_model
     and staging_columns.staging_column = contract_columns.staging_column
    where staging_columns.staging_column is null
),
invalid_raw_leaf_mappings as (
    select
      'invalid_raw_leaf_mapping'::text as issue_type,
      contract.staging_model,
      contract.staging_column,
      contract.raw_path
    from {{ ref('staging_lineage_contract') }} as contract
    left join effective_leaf_paths
      on effective_leaf_paths.connector_id = contract.connector_id
     and effective_leaf_paths.source_stream = contract.source_stream
     and effective_leaf_paths.raw_path = contract.raw_path
    where contract.mapping_type = 'raw_leaf'
      and contract.raw_path not like 'raw.%'
      and effective_leaf_paths.raw_path is null
)
select *
from missing_contract_rows
union all
select *
from orphan_contract_rows
union all
select *
from invalid_raw_leaf_mappings

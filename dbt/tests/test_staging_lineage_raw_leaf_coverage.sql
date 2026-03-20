with _dependency as (
    select 1 from {{ ref('stg_garmin_profile_snapshot') }} where false
    union all select 1 from {{ ref('staging_lineage_contract') }} where false
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
contract_leaf_paths as (
    select distinct
      connector_id,
      source_stream,
      raw_path
    from {{ ref('staging_lineage_contract') }}
    where mapping_type in ('raw_leaf', 'decoded')
      and raw_path not like 'raw.%'
),
missing_raw_leafs as (
    select effective_leaf_paths.*
    from effective_leaf_paths
    left join contract_leaf_paths
      on contract_leaf_paths.connector_id = effective_leaf_paths.connector_id
     and contract_leaf_paths.source_stream = effective_leaf_paths.source_stream
     and contract_leaf_paths.raw_path = effective_leaf_paths.raw_path
    where contract_leaf_paths.raw_path is null
)
select *
from missing_raw_leafs

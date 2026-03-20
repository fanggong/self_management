{{ config(tags=['intermediate', 'health', 'medical_report']) }}

select
  {{ health_intermediate_common_columns('stg') }},
  stg.record_number,
  {{ health_try_date('stg.report_date') }} as report_date,
  stg.institution,
  stg.file_name
from {{ ref('stg_medical_report_snapshot') }} as stg

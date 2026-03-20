{{ config(tags=['intermediate', 'health', 'medical_report']) }}

select
  {{ health_intermediate_common_columns('stg') }},
  stg.record_number,
  {{ health_try_date('stg.report_date') }} as report_date,
  stg.institution,
  stg.file_name,
  stg.section_position,
  stg.item_position,
  stg.section_key,
  stg.section_examiner,
  {{ health_try_date('stg.section_exam_date') }} as section_exam_date,
  stg.item_key,
  stg.result,
  {{ health_try_numeric_range_min('stg.reference_value') }} as reference_value_min,
  {{ health_try_numeric_range_max('stg.reference_value') }} as reference_value_max,
  stg.unit,
  stg.abnormal_flag
from {{ ref('stg_medical_report_item_detail') }} as stg

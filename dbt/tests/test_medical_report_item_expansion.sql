with _dependency as (
    select 1 from {{ ref('stg_medical_report_item_detail') }} limit 0
),
source_row as (
    select
      '{
        "sections": [
          {
            "sectionKey": "general",
            "examiner": "Dr. Chen",
            "examDate": "2026-03-08",
            "items": [
              {"itemKey": "height", "result": "172", "referenceValue": "", "unit": "cm", "abnormalFlag": ""},
              {"itemKey": "weight", "result": "68", "referenceValue": "", "unit": "kg", "abnormalFlag": ""}
            ]
          }
        ]
      }'::jsonb as payload_jsonb
),
section_rows as (
    select
      section_item.ordinality as section_position,
      section_item.value as section_json
    from source_row
    cross join lateral jsonb_array_elements(payload_jsonb -> 'sections') with ordinality as section_item(value, ordinality)
),
item_rows as (
    select
      section_position,
      item_item.ordinality as item_position,
      {{ health_clean_text("section_json ->> 'sectionKey'") }} as section_key,
      {{ health_clean_text("section_json ->> 'examiner'") }} as section_examiner,
      {{ health_clean_text("section_json ->> 'examDate'") }} as section_exam_date,
      {{ health_clean_text("item_item.value ->> 'itemKey'") }} as item_key,
      {{ health_clean_text("item_item.value ->> 'result'") }} as result,
      {{ health_clean_text("item_item.value ->> 'unit'") }} as unit
    from section_rows
    cross join lateral jsonb_array_elements(section_json -> 'items') with ordinality as item_item(value, ordinality)
),
expected as (
    select 1 as section_position, 1 as item_position, 'general'::text as section_key, 'Dr. Chen'::text as section_examiner, '2026-03-08'::text as section_exam_date, 'height'::text as item_key, '172'::text as result, 'cm'::text as unit
    union all
    select 1 as section_position, 2 as item_position, 'general'::text as section_key, 'Dr. Chen'::text as section_examiner, '2026-03-08'::text as section_exam_date, 'weight'::text as item_key, '68'::text as result, 'kg'::text as unit
),
diff as (
    (select * from item_rows except select * from expected)
    union all
    (select * from expected except select * from item_rows)
)
select *
from diff

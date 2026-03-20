DO $$
DECLARE
  target RECORD;
  relation_kind "char";
BEGIN
  FOR target IN
    SELECT *
    FROM (
      VALUES
        ('staging', 'stg_garmin_activity'),
        ('staging', 'stg_garmin_daily_summary'),
        ('staging', 'stg_garmin_heart_rate'),
        ('staging', 'stg_garmin_heart_rate_sample'),
        ('staging', 'stg_garmin_profile'),
        ('staging', 'stg_garmin_sleep'),
        ('staging', 'stg_medical_report_report'),
        ('staging', 'stg_medical_report_item'),
        ('intermediate', 'int_activity_enriched'),
        ('intermediate', 'int_health_daily_user'),
        ('intermediate', 'int_heart_rate_daily'),
        ('intermediate', 'int_sleep_enriched'),
        ('intermediate', 'int_vitals_daily'),
        ('marts', 'dim_user_health_profile'),
        ('marts', 'fct_activity_session'),
        ('marts', 'fct_health_daily_metrics'),
        ('marts', 'fct_sleep_session'),
        ('marts', 'mart_health_dashboard_daily')
    ) AS relations(schema_name, relation_name)
  LOOP
    SELECT c.relkind
    INTO relation_kind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = target.schema_name
      AND c.relname = target.relation_name
    LIMIT 1;

    IF relation_kind = 'v' THEN
      EXECUTE format('DROP VIEW %I.%I CASCADE', target.schema_name, target.relation_name);
    ELSIF relation_kind = 'm' THEN
      EXECUTE format('DROP MATERIALIZED VIEW %I.%I CASCADE', target.schema_name, target.relation_name);
    ELSIF relation_kind IN ('r', 'p') THEN
      EXECUTE format('DROP TABLE %I.%I CASCADE', target.schema_name, target.relation_name);
    END IF;
  END LOOP;
END $$;

CREATE SCHEMA IF NOT EXISTS app;
CREATE SCHEMA IF NOT EXISTS raw;
CREATE SCHEMA IF NOT EXISTS staging;
CREATE SCHEMA IF NOT EXISTS intermediate;
CREATE SCHEMA IF NOT EXISTS marts;

ALTER TABLE IF EXISTS public.app_account SET SCHEMA app;
ALTER TABLE IF EXISTS public.app_user SET SCHEMA app;
ALTER TABLE IF EXISTS public.connector_config SET SCHEMA app;
ALTER TABLE IF EXISTS public.sync_task SET SCHEMA app;

ALTER TABLE IF EXISTS public.raw_sync_task_snapshot SET SCHEMA raw;
ALTER TABLE IF EXISTS public.raw_garmin_profile SET SCHEMA raw;
ALTER TABLE IF EXISTS public.raw_garmin_daily_summary SET SCHEMA raw;

DROP TABLE IF EXISTS public.audit_log CASCADE;
DROP TABLE IF EXISTS public.raw_ingest_event CASCADE;
DROP TABLE IF EXISTS public.raw_garmin_activity CASCADE;
DROP TABLE IF EXISTS public.raw_garmin_activity_detail CASCADE;
DROP TABLE IF EXISTS public.raw_garmin_sleep CASCADE;
DROP TABLE IF EXISTS public.raw_garmin_heart_rate CASCADE;
DROP TABLE IF EXISTS public.raw_garmin_respiration CASCADE;
DROP TABLE IF EXISTS public.raw_garmin_spo2 CASCADE;
DROP TABLE IF EXISTS public.raw_garmin_stress CASCADE;
DROP TABLE IF EXISTS public.raw_garmin_body_composition CASCADE;
DROP TABLE IF EXISTS public.raw_garmin_training_metrics CASCADE;

DO $$
DECLARE
  relation_name TEXT;
  relation_kind "char";
BEGIN
  FOREACH relation_name IN ARRAY ARRAY[
    'stg_garmin_profile',
    'stg_garmin_daily_summary',
    'int_activity_enriched',
    'int_health_daily_user',
    'int_sleep_enriched',
    'int_vitals_daily',
    'dim_user_health_profile',
    'fct_activity_session',
    'fct_health_daily_metrics',
    'fct_sleep_session',
    'mart_health_dashboard_daily'
  ]
  LOOP
    SELECT c.relkind
    INTO relation_kind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relname = relation_name
    LIMIT 1;

    IF relation_kind = 'v' THEN
      EXECUTE format('DROP VIEW public.%I CASCADE', relation_name);
    ELSIF relation_kind = 'm' THEN
      EXECUTE format('DROP MATERIALIZED VIEW public.%I CASCADE', relation_name);
    ELSIF relation_kind = 'r' THEN
      EXECUTE format('DROP TABLE public.%I CASCADE', relation_name);
    END IF;
  END LOOP;
END $$;

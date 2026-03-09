from __future__ import annotations

from contextlib import contextmanager
from datetime import date, datetime
from typing import Any

import psycopg
from psycopg.rows import dict_row
from psycopg.types.json import Jsonb

from .config import settings

APP_SCHEMA = "app"
RAW_SCHEMA = "raw"


@contextmanager
def get_connection():
    connection = psycopg.connect(settings.db_url, autocommit=True, row_factory=dict_row)
    try:
        yield connection
    finally:
        connection.close()


def fetch_queued_task_ids(limit: int = 10) -> list[str]:
    sql = f"""
        SELECT id::text
        FROM {APP_SCHEMA}.sync_task
        WHERE status = 'queued' AND dispatched_at IS NULL
        ORDER BY created_at
        LIMIT %s
    """
    with get_connection() as connection, connection.cursor() as cursor:
        cursor.execute(sql, (limit,))
        return [row["id"] for row in cursor.fetchall()]


def mark_task_dispatched(task_id: str) -> bool:
    sql = f"""
        UPDATE {APP_SCHEMA}.sync_task
        SET dispatched_at = NOW(), updated_at = NOW()
        WHERE id = %s::uuid AND status = 'queued' AND dispatched_at IS NULL
    """
    with get_connection() as connection, connection.cursor() as cursor:
        cursor.execute(sql, (task_id,))
        return cursor.rowcount == 1


def load_task_context(task_id: str) -> dict[str, Any] | None:
    sql = f"""
        SELECT
          st.id::text AS task_id,
          st.account_id::text AS account_id,
          st.connector_config_id::text AS connector_config_id,
          st.trigger_type,
          st.status,
          st.window_start_at,
          st.window_end_at,
          cc.connector_id,
          cc.schedule,
          cc.config_ciphertext
        FROM {APP_SCHEMA}.sync_task st
        JOIN {APP_SCHEMA}.connector_config cc ON cc.id = st.connector_config_id
        WHERE st.id = %s::uuid
    """
    with get_connection() as connection, connection.cursor() as cursor:
        cursor.execute(sql, (task_id,))
        return cursor.fetchone()


def update_task_status(task_id: str, status: str, **fields: Any) -> None:
    assignments = ["status = %s", "updated_at = NOW()"]
    values: list[Any] = [status]

    for key, value in fields.items():
        assignments.append(f"{key} = %s")
        values.append(value)

    values.append(task_id)
    sql = f"UPDATE {APP_SCHEMA}.sync_task SET {', '.join(assignments)} WHERE id = %s::uuid"
    with get_connection() as connection, connection.cursor() as cursor:
        cursor.execute(sql, tuple(values))


def update_connector_run_timestamps(connector_config_id: str, last_run_at: datetime) -> None:
    sql = f"""
        UPDATE {APP_SCHEMA}.connector_config
        SET last_run_at = %s, updated_at = NOW()
        WHERE id = %s::uuid
    """
    with get_connection() as connection, connection.cursor() as cursor:
        cursor.execute(sql, (last_run_at, connector_config_id))


def set_connector_run_timestamps(connector_config_id: str, last_run_at: datetime | None, next_run_at: datetime | None) -> None:
    sql = f"""
        UPDATE {APP_SCHEMA}.connector_config
        SET last_run_at = %s,
            next_run_at = %s,
            updated_at = NOW()
        WHERE id = %s::uuid
    """
    with get_connection() as connection, connection.cursor() as cursor:
        cursor.execute(sql, (last_run_at, next_run_at, connector_config_id))


def fetch_due_running_connectors(limit: int = 20) -> list[dict[str, Any]]:
    sql = f"""
        SELECT
          cc.id::text AS connector_config_id,
          cc.account_id::text AS account_id,
          cc.schedule,
          cc.last_run_at,
          cc.next_run_at
        FROM {APP_SCHEMA}.connector_config cc
        WHERE cc.status = 'running'
          AND cc.next_run_at IS NOT NULL
          AND cc.next_run_at <= NOW()
          AND NOT EXISTS (
            SELECT 1
            FROM {APP_SCHEMA}.sync_task st
            WHERE st.connector_config_id = cc.id
              AND st.status IN ('queued', 'running')
          )
        ORDER BY cc.next_run_at
        LIMIT %s
    """
    with get_connection() as connection, connection.cursor() as cursor:
        cursor.execute(sql, (limit,))
        return list(cursor.fetchall())


def create_scheduled_sync_task(
    account_id: str,
    connector_config_id: str,
    window_start_at: datetime,
    window_end_at: datetime,
) -> str:
    sql = f"""
        INSERT INTO {APP_SCHEMA}.sync_task (
          account_id,
          connector_config_id,
          trigger_type,
          status,
          window_start_at,
          window_end_at
        )
        VALUES (%s::uuid, %s::uuid, 'scheduled', 'queued', %s, %s)
        RETURNING id::text AS id
    """
    with get_connection() as connection, connection.cursor() as cursor:
        cursor.execute(sql, (account_id, connector_config_id, window_start_at, window_end_at))
        row = cursor.fetchone()
        if row is None:
            raise ValueError("Failed to create scheduled sync task.")
        return str(row["id"])


def snapshot_task(task_id: str, account_id: str, connector_config_id: str, payload: dict[str, Any]) -> None:
    sql = f"""
        INSERT INTO {RAW_SCHEMA}.raw_sync_task_snapshot (account_id, connector_config_id, sync_task_id, snapshot_jsonb)
        VALUES (%s::uuid, %s::uuid, %s::uuid, %s)
    """
    with get_connection() as connection, connection.cursor() as cursor:
        cursor.execute(sql, (account_id, connector_config_id, task_id, Jsonb(_json_ready(payload))))


def _upsert_health_record(
    table: str,
    account_id: str,
    connector_config_id: str,
    sync_task_id: str,
    connector_id: str,
    source_stream: str,
    external_id: str,
    source_record_date: str,
    source_record_at: datetime | None,
    source_updated_at: datetime | None,
    payload_hash_value: str,
    payload: dict[str, Any],
) -> str:
    json_payload = _json_ready(payload)
    select_sql = f"""
        SELECT payload_hash
        FROM {RAW_SCHEMA}.{table}
        WHERE account_id = %s::uuid
          AND connector_config_id = %s::uuid
          AND source_stream = %s
          AND source_record_date = %s::date
          AND external_id = %s
    """
    insert_sql = f"""
        INSERT INTO {RAW_SCHEMA}.{table} (
          account_id,
          connector_config_id,
          sync_task_id,
          connector_id,
          source_stream,
          external_id,
          source_record_date,
          source_record_at,
          source_updated_at,
          payload_hash,
          collected_at,
          payload_jsonb
        )
        VALUES (%s::uuid, %s::uuid, %s::uuid, %s, %s, %s, %s::date, %s, %s, %s, NOW(), %s)
    """
    update_sql = f"""
        UPDATE {RAW_SCHEMA}.{table}
        SET sync_task_id = %s::uuid,
            connector_id = %s,
            source_record_at = %s,
            source_updated_at = %s,
            payload_hash = %s,
            collected_at = NOW(),
            payload_jsonb = %s,
            updated_at = NOW()
        WHERE account_id = %s::uuid
          AND connector_config_id = %s::uuid
          AND source_stream = %s
          AND source_record_date = %s::date
          AND external_id = %s
    """
    insert_values = (
        account_id,
        connector_config_id,
        sync_task_id,
        connector_id,
        source_stream,
        external_id,
        source_record_date,
        source_record_at,
        source_updated_at,
        payload_hash_value,
        Jsonb(json_payload),
    )
    update_values = (
        sync_task_id,
        connector_id,
        source_record_at,
        source_updated_at,
        payload_hash_value,
        Jsonb(json_payload),
        account_id,
        connector_config_id,
        source_stream,
        source_record_date,
        external_id,
    )

    with get_connection() as connection, connection.cursor() as cursor:
        cursor.execute(select_sql, (account_id, connector_config_id, source_stream, source_record_date, external_id))
        existing = cursor.fetchone()
        if existing is None:
            cursor.execute(insert_sql, insert_values)
            return "inserted"

        if existing["payload_hash"] == payload_hash_value:
            return "unchanged"

        cursor.execute(update_sql, update_values)
        return "updated"


def upsert_health_snapshot_record(
    account_id: str,
    connector_config_id: str,
    sync_task_id: str,
    connector_id: str,
    source_stream: str,
    external_id: str,
    source_record_date: str,
    source_record_at: datetime | None,
    source_updated_at: datetime | None,
    payload_hash_value: str,
    payload: dict[str, Any],
) -> str:
    return _upsert_health_record(
        "health_snapshot_record",
        account_id,
        connector_config_id,
        sync_task_id,
        connector_id,
        source_stream,
        external_id,
        source_record_date,
        source_record_at,
        source_updated_at,
        payload_hash_value,
        payload,
    )


def upsert_health_event_record(
    account_id: str,
    connector_config_id: str,
    sync_task_id: str,
    connector_id: str,
    source_stream: str,
    external_id: str,
    source_record_date: str,
    source_record_at: datetime | None,
    source_updated_at: datetime | None,
    payload_hash_value: str,
    payload: dict[str, Any],
) -> str:
    return _upsert_health_record(
        "health_event_record",
        account_id,
        connector_config_id,
        sync_task_id,
        connector_id,
        source_stream,
        external_id,
        source_record_date,
        source_record_at,
        source_updated_at,
        payload_hash_value,
        payload,
    )


def upsert_health_timeseries_record(
    account_id: str,
    connector_config_id: str,
    sync_task_id: str,
    connector_id: str,
    source_stream: str,
    external_id: str,
    source_record_date: str,
    source_record_at: datetime | None,
    source_updated_at: datetime | None,
    payload_hash_value: str,
    payload: dict[str, Any],
) -> str:
    return _upsert_health_record(
        "health_timeseries_record",
        account_id,
        connector_config_id,
        sync_task_id,
        connector_id,
        source_stream,
        external_id,
        source_record_date,
        source_record_at,
        source_updated_at,
        payload_hash_value,
        payload,
    )


def _json_ready(value: Any) -> Any:
    if isinstance(value, (datetime, date)):
        return value.isoformat()
    if isinstance(value, dict):
        return {key: _json_ready(item) for key, item in value.items()}
    if isinstance(value, list):
        return [_json_ready(item) for item in value]
    return value

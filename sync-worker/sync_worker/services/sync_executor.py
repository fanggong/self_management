from __future__ import annotations

from datetime import datetime, timezone
from typing import Callable

from ..config import settings
from ..cron import next_run
from ..connectors.garmin import GarminConnectorAdapter, sanitize_garmin_sync_error
from ..crypto import decrypt_config, payload_hash
from .. import db


RawWriter = Callable[..., str]


def execute_sync_task(task_id: str) -> None:
    task = db.load_task_context(task_id)
    if task is None:
        raise ValueError(f"Sync task {task_id} not found.")

    config = decrypt_config(task["config_ciphertext"])
    adapter = GarminConnectorAdapter(config.get("username", ""), config.get("password", ""))

    start_at = task["window_start_at"] or datetime.now(timezone.utc)
    end_at = task["window_end_at"] or datetime.now(timezone.utc)
    if start_at.tzinfo is None:
        start_at = start_at.replace(tzinfo=timezone.utc)
    if end_at.tzinfo is None:
        end_at = end_at.replace(tzinfo=timezone.utc)

    db.update_task_status(task_id, "running", started_at=datetime.now(timezone.utc))
    inserted_count = 0
    updated_count = 0
    unchanged_count = 0
    deduped_count = 0
    fetched_count = 0

    def register_action(action: str) -> None:
        nonlocal inserted_count, updated_count, unchanged_count, deduped_count, fetched_count
        fetched_count += 1
        if action == "inserted":
            inserted_count += 1
        elif action == "updated":
            updated_count += 1
        else:
            unchanged_count += 1
            deduped_count += 1

    def persist_records(records: list[dict[str, object]], writer: RawWriter, source_stream: str) -> None:
        for record in records:
            payload = record["payload"]
            action = writer(
                task["account_id"],
                task["connector_config_id"],
                task_id,
                task["connector_id"],
                source_stream,
                str(record["externalId"]),
                str(record["sourceRecordDate"]),
                record.get("sourceRecordAt"),
                record.get("sourceUpdatedAt"),
                payload_hash(payload),
                payload,
            )
            register_action(action)

    try:
        adapter.verify_connection()
        persist_records([adapter.fetch_profile()], db.upsert_health_snapshot_record, "profile")
        persist_records(adapter.fetch_daily_summaries(start_at, end_at), db.upsert_health_snapshot_record, "daily_summary")
        persist_records(adapter.fetch_body_compositions(start_at, end_at), db.upsert_health_snapshot_record, "body_composition")
        persist_records(adapter.fetch_activities(start_at, end_at), db.upsert_health_event_record, "activity")
        persist_records(adapter.fetch_sleep_sessions(start_at, end_at), db.upsert_health_snapshot_record, "sleep")
        persist_records(adapter.fetch_heart_rates(start_at, end_at), db.upsert_health_timeseries_record, "heart_rate")

        db.snapshot_task(
            task_id,
            task["account_id"],
            task["connector_config_id"],
            {
                "connectorId": task["connector_id"],
                "fetchedCount": fetched_count,
                "insertedCount": inserted_count,
                "updatedCount": updated_count,
                "unchangedCount": unchanged_count,
                "dedupedCount": deduped_count,
            },
        )

        finished_at = datetime.now(timezone.utc)
        next_run_at = next_run(task["schedule"], settings.app_timezone, finished_at)
        db.update_task_status(
            task_id,
            "success",
            finished_at=finished_at,
            fetched_count=fetched_count,
            inserted_count=inserted_count,
            updated_count=updated_count,
            unchanged_count=unchanged_count,
            deduped_count=deduped_count,
            error_code=None,
            error_message=None,
        )
        db.set_connector_run_timestamps(task["connector_config_id"], finished_at, next_run_at)
    except Exception as exc:
        error_code, error_message = sanitize_garmin_sync_error(exc)
        db.update_task_status(
            task_id,
            "failed",
            finished_at=datetime.now(timezone.utc),
            fetched_count=fetched_count,
            inserted_count=inserted_count,
            updated_count=updated_count,
            unchanged_count=unchanged_count,
            deduped_count=deduped_count,
            error_code=error_code,
            error_message=error_message,
        )
        raise RuntimeError(error_message) from None

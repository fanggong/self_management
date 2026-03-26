from __future__ import annotations

import sys
import types
import unittest
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import ANY, patch

sys.path.insert(0, str(Path(__file__).resolve().parent))

if "psycopg" not in sys.modules:
    psycopg_module = types.ModuleType("psycopg")
    psycopg_module.connect = lambda *args, **kwargs: None

    psycopg_rows_module = types.ModuleType("psycopg.rows")
    psycopg_rows_module.dict_row = object()

    psycopg_types_module = types.ModuleType("psycopg.types")
    psycopg_types_json_module = types.ModuleType("psycopg.types.json")

    class Jsonb:  # pragma: no cover - import-time test stub
        def __init__(self, value: object) -> None:
            self.value = value

    psycopg_types_json_module.Jsonb = Jsonb

    sys.modules["psycopg"] = psycopg_module
    sys.modules["psycopg.rows"] = psycopg_rows_module
    sys.modules["psycopg.types"] = psycopg_types_module
    sys.modules["psycopg.types.json"] = psycopg_types_json_module

from sync_worker.services import sync_executor


class ExecuteSyncTaskTest(unittest.TestCase):
    def _task_context(self, trigger_type: str = "manual") -> dict[str, object]:
        return {
            "task_id": "task-1",
            "account_id": "account-1",
            "connector_config_id": "connector-config-1",
            "trigger_type": trigger_type,
            "status": "queued",
            "window_start_at": datetime(2026, 3, 22, 0, 0, tzinfo=timezone.utc),
            "window_end_at": datetime(2026, 3, 23, 0, 0, tzinfo=timezone.utc),
            "connector_id": "garmin-connect",
            "schedule": "0 6 * * *",
            "config_ciphertext": "ciphertext",
        }

    @staticmethod
    def _profile_record() -> dict[str, object]:
        return {
            "externalId": "profile-1",
            "sourceRecordDate": "2026-03-23",
            "sourceRecordAt": None,
            "sourceUpdatedAt": None,
            "payload": {"profile": "payload"},
        }

    def test_execute_sync_task_marks_scheduled_task_success_after_raw_sync(self) -> None:
        next_run_at = datetime(2026, 3, 24, 6, 0, tzinfo=timezone.utc)

        with (
            patch("sync_worker.services.sync_executor.db.load_task_context", return_value=self._task_context("scheduled")),
            patch("sync_worker.services.sync_executor.decrypt_config", return_value={"username": "demo", "password": "secret123"}),
            patch("sync_worker.services.sync_executor.GarminConnectorAdapter") as adapter_class,
            patch("sync_worker.services.sync_executor.db.upsert_health_snapshot_record", return_value="inserted"),
            patch("sync_worker.services.sync_executor.db.upsert_health_event_record", return_value="inserted"),
            patch("sync_worker.services.sync_executor.db.upsert_health_timeseries_record", return_value="inserted"),
            patch("sync_worker.services.sync_executor.db.snapshot_task") as snapshot_task,
            patch("sync_worker.services.sync_executor.db.update_task_status") as update_task_status,
            patch("sync_worker.services.sync_executor.db.set_connector_run_timestamps") as set_connector_run_timestamps,
            patch("sync_worker.services.sync_executor.next_run", return_value=next_run_at),
        ):
            adapter = adapter_class.return_value
            adapter.fetch_profile.return_value = self._profile_record()
            adapter.fetch_daily_summaries.return_value = []
            adapter.fetch_body_compositions.return_value = []
            adapter.fetch_activities.return_value = []
            adapter.fetch_sleep_sessions.return_value = []
            adapter.fetch_heart_rates.return_value = []

            sync_executor.execute_sync_task("task-1")

        self.assertEqual(len(update_task_status.call_args_list), 2)
        running_call, success_call = update_task_status.call_args_list
        self.assertEqual(running_call.args, ("task-1", "running"))
        self.assertIsNotNone(running_call.kwargs["started_at"])

        self.assertEqual(success_call.args, ("task-1", "success"))
        self.assertEqual(success_call.kwargs["fetched_count"], 1)
        self.assertEqual(success_call.kwargs["inserted_count"], 1)
        self.assertEqual(success_call.kwargs["updated_count"], 0)
        self.assertEqual(success_call.kwargs["unchanged_count"], 0)
        self.assertEqual(success_call.kwargs["deduped_count"], 0)
        self.assertIsNone(success_call.kwargs["error_code"])
        self.assertIsNone(success_call.kwargs["error_message"])
        self.assertIsNotNone(success_call.kwargs["finished_at"])

        snapshot_task.assert_called_once_with(
            "task-1",
            "account-1",
            "connector-config-1",
            {
                "connectorId": "garmin-connect",
                "fetchedCount": 1,
                "insertedCount": 1,
                "updatedCount": 0,
                "unchangedCount": 0,
                "dedupedCount": 0,
            },
        )
        set_connector_run_timestamps.assert_called_once_with("connector-config-1", ANY, next_run_at)

    def test_execute_sync_task_marks_task_failed_when_raw_sync_raises(self) -> None:
        with (
            patch("sync_worker.services.sync_executor.db.load_task_context", return_value=self._task_context()),
            patch("sync_worker.services.sync_executor.decrypt_config", return_value={"username": "demo", "password": "secret123"}),
            patch("sync_worker.services.sync_executor.GarminConnectorAdapter") as adapter_class,
            patch("sync_worker.services.sync_executor.db.snapshot_task") as snapshot_task,
            patch("sync_worker.services.sync_executor.db.update_task_status") as update_task_status,
            patch("sync_worker.services.sync_executor.db.set_connector_run_timestamps") as set_connector_run_timestamps,
        ):
            adapter = adapter_class.return_value
            adapter.verify_connection.side_effect = RuntimeError("connector unavailable")

            with self.assertRaisesRegex(RuntimeError, "connector unavailable"):
                sync_executor.execute_sync_task("task-1")

        self.assertEqual(len(update_task_status.call_args_list), 2)
        running_call, failed_call = update_task_status.call_args_list
        self.assertEqual(running_call.args, ("task-1", "running"))
        self.assertEqual(failed_call.args, ("task-1", "failed"))
        self.assertEqual(failed_call.kwargs["fetched_count"], 0)
        self.assertEqual(failed_call.kwargs["inserted_count"], 0)
        self.assertEqual(failed_call.kwargs["updated_count"], 0)
        self.assertEqual(failed_call.kwargs["unchanged_count"], 0)
        self.assertEqual(failed_call.kwargs["deduped_count"], 0)
        self.assertEqual(failed_call.kwargs["error_code"], "SYNC_EXECUTION_FAILED")
        self.assertEqual(failed_call.kwargs["error_message"], "connector unavailable")
        self.assertIsNotNone(failed_call.kwargs["finished_at"])

        snapshot_task.assert_not_called()
        set_connector_run_timestamps.assert_not_called()


if __name__ == "__main__":
    unittest.main()

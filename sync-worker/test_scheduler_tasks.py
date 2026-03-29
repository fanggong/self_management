from __future__ import annotations

import os
import sys
import types
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parent))

os.environ.setdefault("CONNECTOR_SECRET_KEY", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
os.environ.setdefault("INTERNAL_API_TOKEN", "unit-test-internal-token-123456")

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

if "celery" not in sys.modules:
    celery_module = types.ModuleType("celery")
    celery_schedules_module = types.ModuleType("celery.schedules")
    celery_utils_module = types.ModuleType("celery.utils")
    celery_utils_log_module = types.ModuleType("celery.utils.log")

    class FakeCrontab:
        def __init__(self, hour: int, minute: int) -> None:
            self.hour = hour
            self.minute = minute

    class FakeConf:
        def __init__(self) -> None:
            self.beat_schedule = {}
            self.timezone = None

        def update(self, **kwargs: object) -> None:
            for key, value in kwargs.items():
                setattr(self, key, value)

    class FakeCelery:
        def __init__(self, *args: object, **kwargs: object) -> None:
            self.conf = FakeConf()

        def task(self, *args: object, **kwargs: object):
            def decorator(func):
                return func

            return decorator

    celery_module.Celery = FakeCelery
    celery_schedules_module.crontab = FakeCrontab
    celery_utils_log_module.get_task_logger = lambda name: types.SimpleNamespace(info=lambda *a, **k: None)
    celery_utils_module.log = celery_utils_log_module

    sys.modules["celery"] = celery_module
    sys.modules["celery.schedules"] = celery_schedules_module
    sys.modules["celery.utils"] = celery_utils_module
    sys.modules["celery.utils.log"] = celery_utils_log_module

from sync_worker.celery_app import app
from sync_worker.tasks import sync


class SchedulerTasksTest(unittest.TestCase):
    def test_celery_beat_registers_default_marts_schedule_at_noon_shanghai(self) -> None:
        beat_schedule = app.conf.beat_schedule

        self.assertIn("run-default-marts-schedule", beat_schedule)
        self.assertEqual("sync.run_default_marts_schedule", beat_schedule["run-default-marts-schedule"]["task"])
        self.assertEqual(12, beat_schedule["run-default-marts-schedule"]["schedule"].hour)
        self.assertEqual(0, beat_schedule["run-default-marts-schedule"]["schedule"].minute)
        self.assertEqual("Asia/Shanghai", app.conf.timezone)

    def test_trigger_default_marts_schedule_calls_admin_api_and_returns_summary(self) -> None:
        response = Mock()
        response.json.return_value = {
            "success": True,
            "data": {
                "processedAccounts": 2,
                "skippedAccounts": 1,
                "succeededModels": 5,
                "failedModels": 1,
            },
        }

        with (
            patch("sync_worker.tasks.sync.settings", types.SimpleNamespace(admin_api_internal_base_url="http://admin-api:8080")),
            patch("sync_worker.tasks.sync.requests.post", return_value=response) as post,
        ):
            result = sync.trigger_default_marts_schedule()

        post.assert_called_once_with(
            "http://admin-api:8080/internal/dbt/default-schedules/marts/run",
            timeout=(5, sync.DEFAULT_MARTS_SCHEDULE_TIMEOUT_SECONDS),
        )
        response.raise_for_status.assert_called_once()
        self.assertEqual(
            {
                "processedAccounts": 2,
                "skippedAccounts": 1,
                "succeededModels": 5,
                "failedModels": 1,
            },
            result,
        )


if __name__ == "__main__":
    unittest.main()

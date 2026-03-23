from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

import app as runner_app


class DbtRunnerAppTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.project_dir = Path(self.temp_dir.name)
        self.models_dir = self.project_dir / "models"
        self.profiles_dir = self.project_dir / "profiles"
        self.target_dir = self.project_dir / "target"
        self.profiles_dir.mkdir(parents=True)
        self.target_dir.mkdir(parents=True)

        self.original_project_dir = runner_app.PROJECT_DIR
        self.original_models_dir = runner_app.MODELS_DIR
        self.original_profiles_dir = runner_app.PROFILES_DIR
        self.original_target_dir = runner_app.TARGET_DIR
        self.original_run_results_path = runner_app.RUN_RESULTS_PATH
        self.original_manifest_path = runner_app.MANIFEST_PATH
        self.original_model_run_history_path = runner_app.MODEL_RUN_HISTORY_PATH

        runner_app.PROJECT_DIR = self.project_dir
        runner_app.MODELS_DIR = self.models_dir
        runner_app.PROFILES_DIR = self.profiles_dir
        runner_app.TARGET_DIR = self.target_dir
        runner_app.RUN_RESULTS_PATH = self.target_dir / "run_results.json"
        runner_app.MANIFEST_PATH = self.target_dir / "manifest.json"
        runner_app.MODEL_RUN_HISTORY_PATH = self.target_dir / "model_run_history.json"

        self.client = runner_app.app.test_client()

    def tearDown(self) -> None:
        runner_app.PROJECT_DIR = self.original_project_dir
        runner_app.MODELS_DIR = self.original_models_dir
        runner_app.PROFILES_DIR = self.original_profiles_dir
        runner_app.TARGET_DIR = self.original_target_dir
        runner_app.RUN_RESULTS_PATH = self.original_run_results_path
        runner_app.MANIFEST_PATH = self.original_manifest_path
        runner_app.MODEL_RUN_HISTORY_PATH = self.original_model_run_history_path
        self.temp_dir.cleanup()

    def test_normalize_selectors_handles_tags_and_layer_paths(self) -> None:
        self.assertEqual(
            runner_app.normalize_selectors(["tag:staging", " staging/foo ", "+tag:health", "@intermediate/bar"]),
            ["+tag:staging", "path:models/staging/foo", "+tag:health", "@path:models/intermediate/bar"],
        )

    def test_load_model_run_timestamps_keeps_latest_success_per_model(self) -> None:
        runner_app.RUN_RESULTS_PATH.write_text(
            json.dumps(
                {
                    "results": [
                        {
                            "unique_id": "model.otw.stg_alpha",
                            "status": "success",
                            "timing": [{"completed_at": "2026-03-20T10:00:00Z"}],
                        },
                        {
                            "unique_id": "model.otw.stg_alpha",
                            "status": "success",
                            "timing": [{"completed_at": "2026-03-20T12:00:00Z"}],
                        },
                        {
                            "unique_id": "model.otw.int_beta",
                            "status": "error",
                            "timing": [{"completed_at": "2026-03-20T11:00:00Z"}],
                        },
                        {
                            "unique_id": "test.otw.some_test",
                            "status": "success",
                            "timing": [{"completed_at": "2026-03-20T11:00:00Z"}],
                        },
                    ]
                }
            )
        )

        self.assertEqual(runner_app.load_model_run_timestamps(), {"stg_alpha": "2026-03-20T12:00:00Z"})

    def test_list_models_endpoint_filters_by_search(self) -> None:
        (self.models_dir / "staging" / "garmin").mkdir(parents=True)
        (self.models_dir / "staging" / "garmin" / "stg_garmin_profile.sql").write_text("select 1")
        (self.models_dir / "staging" / "garmin" / "stg_garmin_daily_summary.sql").write_text("select 1")
        runner_app.RUN_RESULTS_PATH.write_text(
            json.dumps(
                {
                    "results": [
                        {
                            "unique_id": "model.otw.stg_garmin_profile",
                            "status": "success",
                            "timing": [{"completed_at": "2026-03-20T12:00:00Z"}],
                        }
                    ]
                }
            )
        )

        response = self.client.get("/models?layer=staging&search=profile")
        self.assertEqual(response.status_code, 200)
        payload = response.get_json()
        self.assertTrue(payload["success"])
        self.assertEqual(
            payload["items"],
            [
                {
                    "name": "stg_garmin_profile",
                    "layer": "staging",
                    "lastRunCompletedAt": "2026-03-20T12:00:00Z",
                }
            ],
        )

    def test_load_model_run_timestamps_merges_history_index_with_current_run_results(self) -> None:
        runner_app.MODEL_RUN_HISTORY_PATH.write_text(
            json.dumps({"stg_alpha": "2026-03-20T10:00:00Z"})
        )
        runner_app.RUN_RESULTS_PATH.write_text(
            json.dumps(
                {
                    "results": [
                        {
                            "unique_id": "model.otw.int_beta",
                            "status": "success",
                            "timing": [{"completed_at": "2026-03-20T11:00:00Z"}],
                        }
                    ]
                }
            )
        )

        self.assertEqual(
            runner_app.load_model_run_timestamps(),
            {
                "stg_alpha": "2026-03-20T10:00:00Z",
                "int_beta": "2026-03-20T11:00:00Z",
            },
        )

    def test_run_model_returns_404_for_missing_model(self) -> None:
        response = self.client.post("/models/run", json={"layer": "staging", "modelName": "missing_model"})
        self.assertEqual(response.status_code, 404)
        payload = response.get_json()
        self.assertEqual(payload["code"], "MODEL_NOT_FOUND")

    def test_run_model_returns_busy_when_lock_is_held(self) -> None:
        (self.models_dir / "staging").mkdir(parents=True)
        (self.models_dir / "staging" / "stg_test.sql").write_text("select 1")

        acquired = runner_app.RUNNER_LOCK.acquire(blocking=False)
        self.assertTrue(acquired)
        try:
            response = self.client.post("/models/run", json={"layer": "staging", "modelName": "stg_test"})
        finally:
            runner_app.RUNNER_LOCK.release()

        self.assertEqual(response.status_code, 409)
        payload = response.get_json()
        self.assertEqual(payload["code"], "DBT_RUNNER_BUSY")

    def test_run_model_executes_upstream_dependencies_and_selected_model(self) -> None:
        (self.models_dir / "intermediate" / "health").mkdir(parents=True)
        (self.models_dir / "intermediate" / "health" / "int_health_daily_summary.sql").write_text("select 1")

        with patch("app.subprocess.run") as subprocess_run:
            subprocess_run.return_value.returncode = 0
            subprocess_run.return_value.stdout = "dbt run output"
            subprocess_run.return_value.stderr = ""

            response = self.client.post(
                "/models/run",
                json={"layer": "intermediate", "modelName": "int_health_daily_summary"},
            )

        self.assertEqual(response.status_code, 200)
        payload = response.get_json()
        self.assertTrue(payload["success"])
        self.assertEqual(payload["stdout"], "dbt run output")
        self.assertEqual(payload["executedModels"], [])
        subprocess_run.assert_called_once_with(
            [
                "dbt",
                "run",
                "--project-dir",
                str(self.project_dir),
                "--profiles-dir",
                str(self.profiles_dir),
                "--select",
                "+int_health_daily_summary",
            ],
            capture_output=True,
            text=True,
            check=False,
        )

    def test_run_model_keeps_previous_last_updated_after_another_model_runs(self) -> None:
        (self.models_dir / "staging").mkdir(parents=True)
        (self.models_dir / "staging" / "stg_alpha.sql").write_text("select 1")
        (self.models_dir / "staging" / "stg_beta.sql").write_text("select 1")

        def write_run_results_for_model(model_name: str, completed_at: str) -> None:
            runner_app.RUN_RESULTS_PATH.write_text(
                json.dumps(
                    {
                        "results": [
                            {
                                "unique_id": f"model.otw.{model_name}",
                                "status": "success",
                                "timing": [{"completed_at": completed_at}],
                            }
                        ]
                    }
                )
            )

        invocations = [
            ("stg_alpha", "2026-03-20T10:00:00Z"),
            ("stg_beta", "2026-03-20T11:00:00Z"),
        ]

        def subprocess_side_effect(*args, **kwargs):
            model_name, completed_at = invocations.pop(0)
            write_run_results_for_model(model_name, completed_at)
            completed_process = Mock()
            completed_process.returncode = 0
            completed_process.stdout = "dbt run output"
            completed_process.stderr = ""
            return completed_process

        with patch("app.subprocess.run") as subprocess_run:
            subprocess_run.side_effect = subprocess_side_effect

            first_response = self.client.post("/models/run", json={"layer": "staging", "modelName": "stg_alpha"})
            second_response = self.client.post("/models/run", json={"layer": "staging", "modelName": "stg_beta"})

        self.assertEqual(first_response.status_code, 200)
        self.assertEqual(second_response.status_code, 200)
        self.assertEqual(
            runner_app.load_model_run_timestamps(),
            {
                "stg_alpha": "2026-03-20T10:00:00Z",
                "stg_beta": "2026-03-20T11:00:00Z",
            },
        )

    def test_run_model_returns_executed_models_for_current_run(self) -> None:
        (self.models_dir / "staging" / "garmin").mkdir(parents=True)
        (self.models_dir / "intermediate" / "health" / "garmin").mkdir(parents=True)
        (self.models_dir / "staging" / "garmin" / "stg_garmin_profile_snapshot.sql").write_text("select 1")
        (self.models_dir / "intermediate" / "health" / "garmin" / "int_health_profile_snapshot.sql").write_text("select 1")

        def subprocess_side_effect(*args, **kwargs):
            runner_app.MANIFEST_PATH.write_text(
                json.dumps(
                    {
                        "nodes": {
                            "model.otw.stg_garmin_profile_snapshot": {
                                "original_file_path": "models/staging/garmin/stg_garmin_profile_snapshot.sql"
                            },
                            "model.otw.int_health_profile_snapshot": {
                                "original_file_path": "models/intermediate/health/garmin/int_health_profile_snapshot.sql"
                            },
                        }
                    }
                )
            )
            runner_app.RUN_RESULTS_PATH.write_text(
                json.dumps(
                    {
                        "results": [
                            {
                                "unique_id": "model.otw.stg_garmin_profile_snapshot",
                                "status": "success",
                                "message": None,
                                "relation_name": "staging.stg_garmin_profile_snapshot",
                                "execution_time": 0.06,
                                "timing": [{"completed_at": "2026-03-20T08:00:41Z"}],
                            },
                            {
                                "unique_id": "model.otw.int_health_profile_snapshot",
                                "status": "error",
                                "message": "column missing",
                                "relation_name": None,
                                "execution_time": 0.01,
                                "timing": [{"completed_at": "2026-03-20T08:00:42Z"}],
                            },
                        ]
                    }
                )
            )
            completed_process = Mock()
            completed_process.returncode = 1
            completed_process.stdout = "dbt run output"
            completed_process.stderr = "dbt run failed"
            return completed_process

        with patch("app.subprocess.run") as subprocess_run:
            subprocess_run.side_effect = subprocess_side_effect

            response = self.client.post(
                "/models/run",
                json={"layer": "intermediate", "modelName": "int_health_profile_snapshot"},
            )

        self.assertEqual(response.status_code, 500)
        payload = response.get_json()
        self.assertEqual(
            payload["executedModels"],
            [
                {
                    "uniqueId": "model.otw.stg_garmin_profile_snapshot",
                    "name": "stg_garmin_profile_snapshot",
                    "layer": "staging",
                    "status": "success",
                    "message": None,
                    "relationName": "staging.stg_garmin_profile_snapshot",
                    "executionTimeSeconds": 0.06,
                    "completedAt": "2026-03-20T08:00:41Z",
                },
                {
                    "uniqueId": "model.otw.int_health_profile_snapshot",
                    "name": "int_health_profile_snapshot",
                    "layer": "intermediate",
                    "status": "error",
                    "message": "column missing",
                    "relationName": None,
                    "executionTimeSeconds": 0.01,
                    "completedAt": "2026-03-20T08:00:42Z",
                },
            ],
        )


if __name__ == "__main__":
    unittest.main()

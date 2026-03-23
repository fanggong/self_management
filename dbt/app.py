from __future__ import annotations

import json
import subprocess
import threading
from collections.abc import Iterable
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from flask import Flask, jsonify, request

app = Flask(__name__)


PROJECT_DIR = Path("/app")
PROFILES_DIR = PROJECT_DIR / "profiles"
MODELS_DIR = PROJECT_DIR / "models"
TARGET_DIR = PROJECT_DIR / "target"
RUN_RESULTS_PATH = TARGET_DIR / "run_results.json"
MANIFEST_PATH = TARGET_DIR / "manifest.json"
MODEL_RUN_HISTORY_PATH = TARGET_DIR / "model_run_history.json"
DBT_PACKAGE_NAME = "otw"
ALLOWED_LAYERS = ("staging", "intermediate", "marts")
LAYER_PATH_PREFIXES = tuple(f"{layer}/" for layer in ALLOWED_LAYERS)
RUNNER_LOCK = threading.Lock()


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def normalize_selectors(selectors: Iterable[str]) -> list[str]:
    normalized: list[str] = []
    for raw_selector in selectors:
        selector = str(raw_selector).strip()
        if not selector:
            continue

        prefix = ""
        while selector and selector[0] in "+@":
            prefix += selector[0]
            selector = selector[1:]

        if selector.startswith("tag:"):
            normalized_prefix = prefix if "+" in prefix else f"{prefix}+"
            normalized.append(f"{normalized_prefix}{selector}")
            continue

        if selector.startswith(LAYER_PATH_PREFIXES):
            normalized.append(f"{prefix}path:models/{selector}")
            continue

        normalized.append(f"{prefix}{selector}")

    return normalized


def error_response(status: int, code: str, message: str):
    return jsonify({"success": False, "code": code, "message": message}), status


def validate_layer(raw_layer: str | None) -> str:
    layer = str(raw_layer or "").strip().lower()
    if layer not in ALLOWED_LAYERS:
        raise ValueError("Layer must be one of staging, intermediate, or marts.")
    return layer


def find_models(layer: str, search: str | None = None) -> list[dict[str, Any]]:
    timestamps = load_model_run_timestamps()
    search_value = str(search or "").strip().lower()
    layer_dir = MODELS_DIR / layer
    if not layer_dir.exists():
        return []

    items: list[dict[str, Any]] = []
    for sql_file in sorted(layer_dir.rglob("*.sql")):
        model_name = sql_file.stem
        if search_value and search_value not in model_name.lower():
            continue

        items.append(
            {
                "name": model_name,
                "layer": layer,
                "lastRunCompletedAt": timestamps.get(model_name),
            }
        )

    return items


def load_model_run_timestamps() -> dict[str, str]:
    timestamps = load_timestamp_index(MODEL_RUN_HISTORY_PATH)
    merge_timestamps(timestamps, load_run_results_timestamps())
    return timestamps


def load_run_results_timestamps() -> dict[str, str]:
    if not RUN_RESULTS_PATH.exists():
        return {}

    return extract_model_run_timestamps(load_json_file(RUN_RESULTS_PATH))


def extract_model_run_timestamps(payload: dict[str, Any]) -> dict[str, str]:
    timestamps: dict[str, str] = {}
    for result in payload.get("results", []) if isinstance(payload, dict) else []:
        model_name = extract_model_name(result)
        if not model_name:
            continue

        completed_at = extract_success_completed_at(result)
        if completed_at and completed_at > timestamps.get(model_name, ""):
            timestamps[model_name] = completed_at

    return timestamps


def load_json_file(path: Path) -> dict[str, Any]:
    try:
        payload = json.loads(path.read_text())
    except (OSError, json.JSONDecodeError):
        return {}

    return payload if isinstance(payload, dict) else {}


def load_timestamp_index(path: Path) -> dict[str, str]:
    payload = load_json_file(path)
    timestamps: dict[str, str] = {}
    for model_name, completed_at in payload.items():
        normalized_model_name = str(model_name).strip()
        normalized_completed_at = str(completed_at).strip()
        if normalized_model_name and normalized_completed_at:
            timestamps[normalized_model_name] = normalized_completed_at
    return timestamps


def merge_timestamps(target: dict[str, str], incoming: dict[str, str]) -> None:
    for model_name, completed_at in incoming.items():
        if completed_at > target.get(model_name, ""):
            target[model_name] = completed_at


def persist_model_run_timestamps() -> None:
    timestamps = load_timestamp_index(MODEL_RUN_HISTORY_PATH)
    merge_timestamps(timestamps, load_run_results_timestamps())
    MODEL_RUN_HISTORY_PATH.parent.mkdir(parents=True, exist_ok=True)
    MODEL_RUN_HISTORY_PATH.write_text(json.dumps(timestamps, sort_keys=True))


def extract_model_name(result: dict[str, Any]) -> str | None:
    unique_id = str(result.get("unique_id") or "")
    expected_prefix = f"model.{DBT_PACKAGE_NAME}."
    if not unique_id.startswith(expected_prefix):
        return None

    model_name = unique_id[len(expected_prefix) :]
    return model_name or None


def extract_success_completed_at(result: dict[str, Any]) -> str | None:
    if str(result.get("status") or "").lower() != "success":
        return None

    return extract_latest_completed_at(result)


def extract_latest_completed_at(result: dict[str, Any]) -> str | None:
    latest_completed_at: str | None = None
    for timing in result.get("timing", []):
        completed_at = str(timing.get("completed_at") or "").strip()
        if completed_at and (latest_completed_at is None or completed_at > latest_completed_at):
            latest_completed_at = completed_at

    return latest_completed_at


def get_file_mtime_ns(path: Path) -> int | None:
    try:
        return path.stat().st_mtime_ns
    except OSError:
        return None


def infer_layer_from_path(path_value: Any) -> str | None:
    normalized = str(path_value or "").strip().lstrip("./")
    if not normalized:
        return None

    for layer in ALLOWED_LAYERS:
        if normalized.startswith(f"{layer}/") or normalized.startswith(f"models/{layer}/"):
            return layer

    return None


def load_manifest_model_layers() -> dict[str, str]:
    payload = load_json_file(MANIFEST_PATH)
    layers: dict[str, str] = {}
    nodes = payload.get("nodes", {}) if isinstance(payload, dict) else {}
    if not isinstance(nodes, dict):
        return layers

    for unique_id, node in nodes.items():
        normalized_unique_id = str(unique_id or "").strip()
        if not normalized_unique_id.startswith(f"model.{DBT_PACKAGE_NAME}.") or not isinstance(node, dict):
            continue

        layer = infer_layer_from_path(node.get("original_file_path")) or infer_layer_from_path(node.get("path"))
        if layer:
            layers[normalized_unique_id] = layer

    return layers


def find_model_layer_by_name(model_name: str) -> str | None:
    normalized_name = str(model_name or "").strip()
    if not normalized_name:
        return None

    for layer in ALLOWED_LAYERS:
        if any((MODELS_DIR / layer).rglob(f"{normalized_name}.sql")):
            return layer

    return None


def extract_executed_models(payload: dict[str, Any]) -> list[dict[str, Any]]:
    manifest_layers = load_manifest_model_layers()
    executed_models: list[dict[str, Any]] = []

    for result in payload.get("results", []) if isinstance(payload, dict) else []:
        if not isinstance(result, dict):
            continue

        unique_id = str(result.get("unique_id") or "").strip()
        model_name = extract_model_name(result)
        if not unique_id or not model_name:
            continue

        layer = manifest_layers.get(unique_id) or find_model_layer_by_name(model_name)
        if layer is None:
            continue

        executed_models.append(
            {
                "uniqueId": unique_id,
                "name": model_name,
                "layer": layer,
                "status": str(result.get("status") or "").strip().lower() or None,
                "message": str(result.get("message") or "").strip() or None,
                "relationName": str(result.get("relation_name") or "").strip() or None,
                "executionTimeSeconds": result.get("execution_time"),
                "completedAt": extract_latest_completed_at(result),
            }
        )

    return executed_models


def load_current_run_results(previous_mtime_ns: int | None) -> dict[str, Any]:
    current_mtime_ns = get_file_mtime_ns(RUN_RESULTS_PATH)
    if current_mtime_ns is None:
        return {}

    if previous_mtime_ns is not None and current_mtime_ns <= previous_mtime_ns:
        return {}

    return load_json_file(RUN_RESULTS_PATH)


def execute_dbt_command(command: list[str], *, busy_message: str, include_executed_models: bool = False):
    started_at = utc_now_iso()
    if not RUNNER_LOCK.acquire(blocking=False):
        return error_response(409, "DBT_RUNNER_BUSY", busy_message)

    previous_run_results_mtime_ns = get_file_mtime_ns(RUN_RESULTS_PATH)
    try:
        process = subprocess.run(command, capture_output=True, text=True, check=False)
        persist_model_run_timestamps()
    finally:
        RUNNER_LOCK.release()

    finished_at = utc_now_iso()
    success = process.returncode == 0
    payload = {
        "success": success,
        "returncode": process.returncode,
        "stdout": process.stdout,
        "stderr": process.stderr,
        "startedAt": started_at,
        "finishedAt": finished_at,
    }
    if include_executed_models:
        payload["executedModels"] = extract_executed_models(load_current_run_results(previous_run_results_mtime_ns))

    status = 200 if success else 500
    return jsonify(payload), status


@app.get("/health")
def health() -> tuple[dict[str, str], int]:
    return {"status": "ok"}, 200


@app.get("/models")
def list_models():
    try:
        layer = validate_layer(request.args.get("layer"))
    except ValueError as exception:
        return error_response(400, "INVALID_LAYER", str(exception))

    search = request.args.get("search")
    return jsonify({"success": True, "items": find_models(layer, search)}), 200


@app.post("/models/run")
def run_model():
    payload = request.get_json(silent=True) or {}

    try:
        layer = validate_layer(payload.get("layer"))
    except ValueError as exception:
        return error_response(400, "INVALID_LAYER", str(exception))

    model_name = str(payload.get("modelName") or "").strip()
    if not model_name:
        return error_response(400, "INVALID_MODEL_NAME", "Model name is required.")

    matching_files = list((MODELS_DIR / layer).rglob(f"{model_name}.sql"))
    if not matching_files:
        return error_response(404, "MODEL_NOT_FOUND", "Model not found in the requested layer.")

    command = [
        "dbt",
        "run",
        "--project-dir",
        str(PROJECT_DIR),
        "--profiles-dir",
        str(PROFILES_DIR),
        "--select",
        f"+{model_name}",
    ]
    return execute_dbt_command(
        command,
        busy_message="Another dbt command is already running.",
        include_executed_models=True,
    )


@app.post("/run")
def run_build():
    payload = request.get_json(silent=True) or {}
    selected = normalize_selectors(payload.get("select") or [])

    command = [
        "dbt",
        "build",
        "--project-dir",
        str(PROJECT_DIR),
        "--profiles-dir",
        str(PROFILES_DIR),
    ]
    if selected:
        command.extend(["--select", " ".join(selected)])

    return execute_dbt_command(command, busy_message="Another dbt command is already running.")


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8090)

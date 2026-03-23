from __future__ import annotations

import csv
import json
import queue
import re
import subprocess
import threading
from collections.abc import Callable, Iterable
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from flask import Flask, Response, jsonify, request, stream_with_context

app = Flask(__name__)


PROJECT_DIR = Path("/app")
PROFILES_DIR = PROJECT_DIR / "profiles"
MODELS_DIR = PROJECT_DIR / "models"
TARGET_DIR = PROJECT_DIR / "target"
RUN_RESULTS_PATH = TARGET_DIR / "run_results.json"
MANIFEST_PATH = TARGET_DIR / "manifest.json"
CATALOG_PATH = TARGET_DIR / "catalog.json"
MODEL_RUN_HISTORY_PATH = TARGET_DIR / "model_run_history.json"
STAGING_LINEAGE_CONTRACT_PATH = PROJECT_DIR / "seeds" / "staging_lineage_contract.csv"
DBT_PACKAGE_NAME = "otw"
ALLOWED_LAYERS = ("staging", "intermediate", "marts")
LAYER_PATH_PREFIXES = tuple(f"{layer}/" for layer in ALLOWED_LAYERS)
KNOWN_DOMAIN_KEYS = ("health", "finance")
RUNNER_LOCK = threading.Lock()
ANSI_ESCAPE_PATTERN = re.compile(r"\x1B\[[0-?]*[ -/]*[@-~]")


@dataclass
class RunningCommand:
    process: subprocess.Popen[str]
    started_at: str
    previous_run_results_mtime_ns: int | None


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


def strip_ansi(value: str) -> str:
    return ANSI_ESCAPE_PATTERN.sub("", value or "")


def encode_ndjson_line(payload: dict[str, Any]) -> str:
    return json.dumps(payload) + "\n"


def validate_layer(raw_layer: str | None) -> str:
    layer = str(raw_layer or "").strip().lower()
    if layer not in ALLOWED_LAYERS:
        raise ValueError("Layer must be one of staging, intermediate, or marts.")
    return layer


def find_models(layer: str, search: str | None = None) -> list[dict[str, Any]]:
    timestamps = load_model_run_timestamps()
    metadata_index = load_model_metadata_index()
    staging_connector_map = load_staging_connector_map()
    search_value = str(search or "").strip().lower()
    layer_dir = MODELS_DIR / layer
    if not layer_dir.exists():
        return []

    items: list[dict[str, Any]] = []
    for sql_file in sorted(layer_dir.rglob("*.sql")):
        model_name = sql_file.stem
        if search_value and search_value not in model_name.lower():
            continue

        metadata = metadata_index.get((layer, model_name), {})
        connector_id = metadata.get("connectorId")
        if connector_id is None and layer == "staging":
            connector_id = staging_connector_map.get(model_name)

        items.append(
            {
                "name": model_name,
                "layer": layer,
                "description": metadata.get("description"),
                "connectorId": connector_id if layer == "staging" else None,
                "domainKey": metadata.get("domainKey") if layer != "staging" else None,
                "lastRunCompletedAt": timestamps.get(model_name),
            }
        )

    return items


def get_model_detail(layer: str, model_name: str) -> dict[str, Any] | None:
    matching_files = list((MODELS_DIR / layer).rglob(f"{model_name}.sql"))
    if not matching_files:
        return None

    metadata_index = load_model_metadata_index()
    staging_connector_map = load_staging_connector_map()
    metadata = metadata_index.get((layer, model_name), {})
    sql_file = matching_files[0]
    relative_path = sql_file.relative_to(MODELS_DIR).as_posix()
    connector_id = metadata.get("connectorId")
    if connector_id is None and layer == "staging":
        connector_id = staging_connector_map.get(model_name)
    domain_key = metadata.get("domainKey") or infer_domain_key(None, relative_path)
    if domain_key is None:
        domain_key = infer_domain_from_connector(connector_id)

    return {
        "name": model_name,
        "layer": layer,
        "description": metadata.get("description"),
        "connectorId": connector_id if layer == "staging" else None,
        "domainKey": domain_key if layer != "staging" else domain_key,
        "schemaName": metadata.get("schemaName") or layer,
        "relationName": metadata.get("relationName") or model_name,
        "columns": metadata.get("columns") or [],
    }


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


def normalize_nullable(value: Any) -> str | None:
    normalized = str(value or "").strip()
    return normalized or None


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


def infer_domain_key(tags: Any, path_value: Any) -> str | None:
    if isinstance(tags, list):
        for tag in tags:
            normalized_tag = normalize_nullable(tag)
            if normalized_tag and normalized_tag.lower() in KNOWN_DOMAIN_KEYS:
                return normalized_tag.lower()

    normalized_path = str(path_value or "").strip().lstrip("./")
    if not normalized_path:
        return None

    segments = [segment for segment in normalized_path.split("/") if segment]
    for segment in segments:
        lowered = segment.lower()
        if lowered in KNOWN_DOMAIN_KEYS:
            return lowered

    return None


def infer_domain_from_connector(connector_id: str | None) -> str | None:
    normalized_connector_id = normalize_nullable(connector_id)
    if normalized_connector_id in {"garmin-connect", "medical-report"}:
        return "health"

    return None


def load_staging_connector_map() -> dict[str, str]:
    if not STAGING_LINEAGE_CONTRACT_PATH.exists():
        return {}

    connectors_by_model: dict[str, set[str]] = {}
    try:
        with STAGING_LINEAGE_CONTRACT_PATH.open(newline="") as csv_file:
            for row in csv.DictReader(csv_file):
                model_name = normalize_nullable(row.get("staging_model"))
                connector_id = normalize_nullable(row.get("connector_id"))
                if not model_name or not connector_id:
                    continue
                connectors_by_model.setdefault(model_name, set()).add(connector_id)
    except OSError:
        return {}

    return {
        model_name: ", ".join(sorted(connector_ids))
        for model_name, connector_ids in connectors_by_model.items()
    }


def extract_catalog_column_type(catalog_column: Any) -> str | None:
    if not isinstance(catalog_column, dict):
        return None

    return normalize_nullable(catalog_column.get("type"))


def extract_catalog_column_comment(catalog_column: Any) -> str | None:
    if not isinstance(catalog_column, dict):
        return None

    return normalize_nullable(catalog_column.get("comment"))


def extract_model_columns(manifest_node: dict[str, Any], catalog_node: dict[str, Any]) -> list[dict[str, Any]]:
    manifest_columns = manifest_node.get("columns", {})
    catalog_columns = catalog_node.get("columns", {}) if isinstance(catalog_node, dict) else {}
    items: list[dict[str, Any]] = []
    seen: set[str] = set()

    if isinstance(manifest_columns, dict):
        for column_name, column_meta in manifest_columns.items():
            normalized_column_name = normalize_nullable(column_name)
            if not normalized_column_name:
                continue

            catalog_column = catalog_columns.get(normalized_column_name, {}) if isinstance(catalog_columns, dict) else {}
            description = None
            if isinstance(column_meta, dict):
                description = normalize_nullable(column_meta.get("description"))
            if description is None:
                description = extract_catalog_column_comment(catalog_column)

            items.append(
                {
                    "name": normalized_column_name,
                    "type": extract_catalog_column_type(catalog_column),
                    "description": description,
                }
            )
            seen.add(normalized_column_name)

    if isinstance(catalog_columns, dict):
        for column_name, column_meta in catalog_columns.items():
            normalized_column_name = normalize_nullable(column_name)
            if not normalized_column_name or normalized_column_name in seen:
                continue

            items.append(
                {
                    "name": normalized_column_name,
                    "type": extract_catalog_column_type(column_meta),
                    "description": extract_catalog_column_comment(column_meta),
                }
            )

    return items


def load_model_metadata_index() -> dict[tuple[str, str], dict[str, Any]]:
    manifest_payload = load_json_file(MANIFEST_PATH)
    catalog_payload = load_json_file(CATALOG_PATH)
    nodes = manifest_payload.get("nodes", {}) if isinstance(manifest_payload, dict) else {}
    catalog_nodes = catalog_payload.get("nodes", {}) if isinstance(catalog_payload, dict) else {}
    if not isinstance(nodes, dict):
        return {}

    items: dict[tuple[str, str], dict[str, Any]] = {}
    for unique_id, node in nodes.items():
        normalized_unique_id = normalize_nullable(unique_id)
        if not normalized_unique_id or not normalized_unique_id.startswith(f"model.{DBT_PACKAGE_NAME}."):
            continue
        if not isinstance(node, dict):
            continue

        model_name = normalize_nullable(node.get("name"))
        layer = infer_layer_from_path(node.get("original_file_path")) or infer_layer_from_path(node.get("path"))
        if not model_name or layer is None:
            continue

        catalog_node = catalog_nodes.get(normalized_unique_id, {}) if isinstance(catalog_nodes, dict) else {}
        items[(layer, model_name)] = {
            "description": normalize_nullable(node.get("description")),
            "domainKey": infer_domain_key(node.get("tags"), node.get("original_file_path") or node.get("path")),
            "schemaName": normalize_nullable(node.get("schema")) or layer,
            "relationName": normalize_nullable(node.get("alias")) or model_name,
            "columns": extract_model_columns(node, catalog_node if isinstance(catalog_node, dict) else {}),
        }

    return items


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


def start_dbt_command(command: list[str], *, busy_message: str):
    if not RUNNER_LOCK.acquire(blocking=False):
        return None, error_response(409, "DBT_RUNNER_BUSY", busy_message)

    try:
        process = subprocess.Popen(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
        )
    except OSError as exception:
        RUNNER_LOCK.release()
        return None, error_response(500, "DBT_COMMAND_START_FAILED", f"Unable to start dbt command: {exception}")

    return (
        RunningCommand(
            process=process,
            started_at=utc_now_iso(),
            previous_run_results_mtime_ns=get_file_mtime_ns(RUN_RESULTS_PATH),
        ),
        None,
    )


def consume_process_output(
    process: subprocess.Popen[str],
    on_log: Callable[[dict[str, Any]], None] | None = None,
) -> tuple[str, str, int]:
    stream_queue: queue.Queue[tuple[str, str | None]] = queue.Queue()
    stdout_chunks: list[str] = []
    stderr_chunks: list[str] = []

    def drain_stream(stream_name: str, stream: Any) -> None:
        try:
            if stream is None:
                return

            for chunk in iter(stream.readline, ""):
                stream_queue.put((stream_name, chunk))
        finally:
            try:
                if stream is not None:
                    stream.close()
            finally:
                stream_queue.put((stream_name, None))

    stdout_thread = threading.Thread(target=drain_stream, args=("stdout", process.stdout), daemon=True)
    stderr_thread = threading.Thread(target=drain_stream, args=("stderr", process.stderr), daemon=True)
    stdout_thread.start()
    stderr_thread.start()

    finished_streams = 0
    while finished_streams < 2:
        stream_name, chunk = stream_queue.get()
        if chunk is None:
            finished_streams += 1
            continue

        if stream_name == "stdout":
            stdout_chunks.append(chunk)
        else:
            stderr_chunks.append(chunk)

        if on_log is not None:
            on_log(
                {
                    "type": "log",
                    "stream": stream_name,
                    "text": strip_ansi(chunk),
                    "timestamp": utc_now_iso(),
                }
            )

    process.wait()
    stdout_thread.join()
    stderr_thread.join()
    return "".join(stdout_chunks), "".join(stderr_chunks), process.returncode


def build_run_payload(
    process: subprocess.Popen[str],
    *,
    started_at: str,
    previous_run_results_mtime_ns: int | None,
    stdout: str,
    stderr: str,
    include_executed_models: bool = False,
) -> tuple[int, dict[str, Any]]:
    finished_at = utc_now_iso()
    success = process.returncode == 0
    payload = {
        "success": success,
        "returncode": process.returncode,
        "stdout": stdout,
        "stderr": stderr,
        "startedAt": started_at,
        "finishedAt": finished_at,
    }
    if include_executed_models:
        payload["executedModels"] = extract_executed_models(load_current_run_results(previous_run_results_mtime_ns))

    status = 200 if success else 500
    return status, payload


def execute_dbt_command(command: list[str], *, busy_message: str, include_executed_models: bool = False):
    running_command, error = start_dbt_command(command, busy_message=busy_message)
    if error is not None:
        return error

    try:
        stdout, stderr, _ = consume_process_output(running_command.process)
        persist_model_run_timestamps()
    finally:
        RUNNER_LOCK.release()

    status, payload = build_run_payload(
        running_command.process,
        started_at=running_command.started_at,
        previous_run_results_mtime_ns=running_command.previous_run_results_mtime_ns,
        stdout=stdout,
        stderr=stderr,
        include_executed_models=include_executed_models,
    )
    return jsonify(payload), status


def stream_dbt_command(command: list[str], *, busy_message: str, layer: str, model_name: str, include_executed_models: bool = False):
    running_command, error = start_dbt_command(command, busy_message=busy_message)
    if error is not None:
        return error

    event_queue: queue.Queue[dict[str, Any] | None] = queue.Queue()

    def worker() -> None:
        try:
            event_queue.put(
                {
                    "type": "run_started",
                    "layer": layer,
                    "modelName": model_name,
                    "startedAt": running_command.started_at,
                }
            )
            stdout, stderr, _ = consume_process_output(running_command.process, on_log=event_queue.put)
            persist_model_run_timestamps()
            status, payload = build_run_payload(
                running_command.process,
                started_at=running_command.started_at,
                previous_run_results_mtime_ns=running_command.previous_run_results_mtime_ns,
                stdout=stdout,
                stderr=stderr,
                include_executed_models=include_executed_models,
            )
            event_queue.put(
                {
                    "type": "run_finished",
                    "layer": layer,
                    "modelName": model_name,
                    "statusCode": status,
                    **payload,
                }
            )
        except Exception as exception:  # pragma: no cover - defensive guard for streamed runs
            event_queue.put(
                {
                    "type": "run_finished",
                    "layer": layer,
                    "modelName": model_name,
                    "statusCode": 500,
                    "success": False,
                    "returncode": None,
                    "stdout": "",
                    "stderr": "",
                    "startedAt": running_command.started_at,
                    "finishedAt": utc_now_iso(),
                    "code": "DBT_STREAM_EXECUTION_FAILED",
                    "message": f"Unable to stream dbt command output: {exception}",
                    "executedModels": [],
                }
            )
        finally:
            RUNNER_LOCK.release()
            event_queue.put(None)

    threading.Thread(target=worker, daemon=True).start()

    def generate():
        while True:
            event = event_queue.get()
            if event is None:
                break
            yield encode_ndjson_line(event)

    return Response(stream_with_context(generate()), mimetype="application/x-ndjson")


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


@app.get("/models/<layer>/<model_name>")
def get_model(layer: str, model_name: str):
    try:
        normalized_layer = validate_layer(layer)
    except ValueError as exception:
        return error_response(400, "INVALID_LAYER", str(exception))

    normalized_model_name = str(model_name or "").strip()
    if not normalized_model_name:
        return error_response(400, "INVALID_MODEL_NAME", "Model name is required.")

    item = get_model_detail(normalized_layer, normalized_model_name)
    if item is None:
        return error_response(404, "MODEL_NOT_FOUND", "Model not found in the requested layer.")

    return jsonify({"success": True, "item": item}), 200


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


@app.post("/models/run/stream")
def run_model_stream():
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
    return stream_dbt_command(
        command,
        busy_message="Another dbt command is already running.",
        layer=layer,
        model_name=model_name,
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

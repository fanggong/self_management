from __future__ import annotations

import subprocess
from collections.abc import Iterable

from flask import Flask, jsonify, request

app = Flask(__name__)


LAYER_PATH_PREFIXES = ("staging/", "intermediate/", "marts/")


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


@app.get("/health")
def health() -> tuple[dict[str, str], int]:
    return {"status": "ok"}, 200


@app.post("/run")
def run_build():
    payload = request.get_json(silent=True) or {}
    selected = normalize_selectors(payload.get("select") or [])

    command = [
        "dbt",
        "build",
        "--project-dir",
        "/app",
        "--profiles-dir",
        "/app/profiles",
    ]
    if selected:
        command.extend(["--select", " ".join(selected)])

    process = subprocess.run(command, capture_output=True, text=True, check=False)
    status = 200 if process.returncode == 0 else 500
    return (
        jsonify(
            {
                "success": process.returncode == 0,
                "returncode": process.returncode,
                "stdout": process.stdout,
                "stderr": process.stderr,
            }
        ),
        status,
    )


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8090)

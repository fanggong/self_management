from __future__ import annotations

import subprocess

from flask import Flask, jsonify, request

app = Flask(__name__)


@app.get("/health")
def health() -> tuple[dict[str, str], int]:
    return {"status": "ok"}, 200


@app.post("/run")
def run_build():
    payload = request.get_json(silent=True) or {}
    selected = payload.get("select") or []

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

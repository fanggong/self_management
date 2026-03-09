#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

echo "[1/4] docker compose config"
docker compose config >/tmp/otw-compose-config.txt

echo "[2/4] frontend typecheck"
(
  cd frontend
  npm run typecheck
)

echo "[3/4] sync-worker python compile"
python3 -m compileall sync-worker/sync_worker >/tmp/otw-python-compile.txt

echo "[4/4] dbt-runner flask app compile"
python3 -m py_compile dbt/app.py

echo "v0.1.1 verification completed"

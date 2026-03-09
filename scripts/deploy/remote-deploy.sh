#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="${1:-/srv/self_management}"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required on the deployment target" >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "docker compose is required on the deployment target" >&2
  exit 1
fi

if [[ ! -d "$PROJECT_DIR" ]]; then
  echo "project directory does not exist: $PROJECT_DIR" >&2
  exit 1
fi

cd "$PROJECT_DIR"

if [[ ! -f docker-compose.yml ]]; then
  echo "docker-compose.yml not found in $PROJECT_DIR" >&2
  exit 1
fi

docker compose config >/dev/null
docker compose up -d --build --remove-orphans
docker compose ps

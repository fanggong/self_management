from __future__ import annotations

import json
import logging
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

from .connectors.garmin import GarminConnectorAdapter

logger = logging.getLogger(__name__)


class InternalApiHandler(BaseHTTPRequestHandler):
    server_version = "OTWSyncWorker/0.1"

    def do_GET(self) -> None:  # noqa: N802
        if self.path == "/health":
            self._write_json(HTTPStatus.OK, {"success": True, "message": "ok"})
            return

        self._write_json(HTTPStatus.NOT_FOUND, {"success": False, "code": "NOT_FOUND", "message": "Not found."})

    def do_POST(self) -> None:  # noqa: N802
        if self.path != "/internal/connectors/garmin-connect/verify":
            self._write_json(HTTPStatus.NOT_FOUND, {"success": False, "code": "NOT_FOUND", "message": "Not found."})
            return

        try:
            payload = self._read_json()
        except ValueError as error:
            self._write_json(
                HTTPStatus.BAD_REQUEST,
                {"success": False, "code": "VALIDATION_ERROR", "message": str(error) or "Invalid JSON payload."},
            )
            return

        config = payload.get("config") if isinstance(payload, dict) else None
        username = str((config or {}).get("username", "")).strip()
        password = str((config or {}).get("password", ""))

        if not username:
            self._write_json(
                HTTPStatus.BAD_REQUEST,
                {"success": False, "code": "CONNECTOR_VALIDATION_ERROR", "message": "Username is required."},
            )
            return

        if len(password) < 6:
            self._write_json(
                HTTPStatus.BAD_REQUEST,
                {"success": False, "code": "CONNECTOR_VALIDATION_ERROR", "message": "Password must be at least 6 characters."},
            )
            return

        try:
            GarminConnectorAdapter(username, password).verify_connection()
        except Exception as error:  # pragma: no cover - runtime integration branch
            logger.warning("Garmin connection verification failed for %s: %s", username, error)
            status, code, message = self._map_verification_error(error)
            self._write_json(status, {"success": False, "code": code, "message": message})
            return

        self._write_json(
            HTTPStatus.OK,
            {"success": True, "code": "CONNECTOR_VERIFIED", "message": "Garmin Connect credentials verified successfully."},
        )

    def log_message(self, format: str, *args: Any) -> None:  # noqa: A003
        logger.info("%s - %s", self.address_string(), format % args)

    def _read_json(self) -> Any:
        content_length = int(self.headers.get("Content-Length", "0"))
        raw_body = self.rfile.read(content_length) if content_length > 0 else b"{}"
        try:
            return json.loads(raw_body.decode("utf-8"))
        except json.JSONDecodeError as error:
            raise ValueError("Invalid JSON payload.") from error

    def _write_json(self, status: HTTPStatus, payload: dict[str, Any]) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status.value)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    @staticmethod
    def _map_verification_error(error: Exception) -> tuple[HTTPStatus, str, str]:
        raw_message = str(error).strip()
        message = raw_message.lower()

        if any(token in message for token in ("credential", "password", "username", "auth", "login", "401", "403", "forbidden", "unauthorized")):
            return (
                HTTPStatus.BAD_REQUEST,
                "CONNECTOR_AUTH_FAILED",
                "Garmin Connect rejected the provided username or password.",
            )

        if any(token in message for token in ("timeout", "connection", "network", "proxy", "temporarily", "rate")):
            return (
                HTTPStatus.BAD_GATEWAY,
                "CONNECTOR_CONNECTION_ERROR",
                "Unable to reach Garmin Connect right now. Please try again.",
            )

        return (
            HTTPStatus.BAD_GATEWAY,
            "CONNECTOR_VERIFICATION_FAILED",
            raw_message or "Garmin Connect verification failed.",
        )


def main() -> None:
    logging.basicConfig(level=logging.INFO)
    server = ThreadingHTTPServer(("0.0.0.0", 8081), InternalApiHandler)
    logger.info("starting sync-worker internal API on :8081")
    server.serve_forever()


if __name__ == "__main__":
    main()

from __future__ import annotations

import json
import logging
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

from .config import settings
from .connectors.garmin import GarminConnectorAdapter, sanitize_garmin_sync_error
from .connectors.medical_report import MedicalReportConnectorAdapter, MedicalReportConnectorError, decode_pdf_base64

logger = logging.getLogger(__name__)


class InternalApiHandler(BaseHTTPRequestHandler):
    server_version = "OTWSyncWorker/0.1"

    def do_GET(self) -> None:  # noqa: N802
        if self.path == "/health":
            self._write_json(HTTPStatus.OK, {"success": True, "message": "ok"})
            return

        self._write_json(HTTPStatus.NOT_FOUND, {"success": False, "code": "NOT_FOUND", "message": "Not found."})

    def do_POST(self) -> None:  # noqa: N802
        if self.path not in (
            "/internal/connectors/garmin-connect/verify",
            "/internal/connectors/medical-report/verify",
            "/internal/connectors/medical-report/parse",
        ):
            self._write_json(HTTPStatus.NOT_FOUND, {"success": False, "code": "NOT_FOUND", "message": "Not found."})
            return

        if self.headers.get("X-Internal-Token", "").strip() != settings.internal_api_token:
            self._write_json(
                HTTPStatus.FORBIDDEN,
                {"success": False, "code": "FORBIDDEN", "message": "Invalid internal API token."},
            )
            return

        try:
            payload = self._read_json()
        except ValueError as error:
            self._write_json(
                HTTPStatus.BAD_REQUEST,
                {"success": False, "code": "VALIDATION_ERROR", "message": str(error) or "Invalid JSON payload."},
            )
            return

        if self.path == "/internal/connectors/garmin-connect/verify":
            self._verify_garmin(payload)
            return

        if self.path == "/internal/connectors/medical-report/parse":
            self._parse_medical_report(payload)
            return

        self._verify_medical_report(payload)

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
    def _read_text(payload: Any, *keys: str) -> str:
        if not isinstance(payload, dict):
            return ""

        for key in keys:
            value = payload.get(key)
            if value is None:
                continue
            normalized = str(value).strip()
            if normalized:
                return normalized

        return ""

    @staticmethod
    def _map_verification_error(error: Exception) -> tuple[HTTPStatus, str, str]:
        error_code, _ = sanitize_garmin_sync_error(error)

        if error_code == "CONNECTOR_AUTH_FAILED":
            return (
                HTTPStatus.BAD_REQUEST,
                error_code,
                "Garmin Connect rejected the provided username or password.",
            )

        if error_code == "CONNECTOR_CONNECTION_ERROR":
            return (
                HTTPStatus.BAD_GATEWAY,
                error_code,
                "Unable to reach Garmin Connect right now. Please try again.",
            )

        return (
            HTTPStatus.BAD_GATEWAY,
            "CONNECTOR_VERIFICATION_FAILED",
            "Garmin Connect verification failed.",
        )

    def _verify_garmin(self, payload: Any) -> None:
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
            status, code, message = self._map_verification_error(error)
            logger.warning("Garmin connection verification failed with code=%s", code)
            self._write_json(status, {"success": False, "code": code, "message": message})
            return

        self._write_json(
            HTTPStatus.OK,
            {"success": True, "code": "CONNECTOR_VERIFIED", "message": "Garmin Connect credentials verified successfully."},
        )

    def _verify_medical_report(self, payload: Any) -> None:
        config = payload.get("config") if isinstance(payload, dict) else None
        provider = self._read_text(config, "provider").lower()
        model_id = self._read_text(config, "modelId", "model_id")
        api_key = self._read_text(config, "apiKey", "api_key")

        try:
            MedicalReportConnectorAdapter(provider=provider, model_id=model_id, api_key=api_key).verify_connection()
        except MedicalReportConnectorError as error:
            self._write_json(
                HTTPStatus(error.http_status),
                {
                    "success": False,
                    "code": error.code,
                    "message": error.message,
                },
            )
            return

        self._write_json(
            HTTPStatus.OK,
            {"success": True, "code": "CONNECTOR_VERIFIED", "message": "Medical Report model credentials verified successfully."},
        )

    def _parse_medical_report(self, payload: Any) -> None:
        config = payload.get("config") if isinstance(payload, dict) else None
        provider = self._read_text(config, "provider").lower()
        model_id = self._read_text(config, "modelId", "model_id")
        api_key = self._read_text(config, "apiKey", "api_key")
        record_number = self._read_text(payload, "recordNumber", "record_number")
        report_date = self._read_text(payload, "reportDate", "report_date")
        institution = self._read_text(payload, "institution")
        file_name = self._read_text(payload, "fileName", "file_name")
        file_base64 = self._read_text(payload, "fileBase64", "file_base64")

        if not record_number:
            self._write_json(
                HTTPStatus.BAD_REQUEST,
                {"success": False, "code": "VALIDATION_ERROR", "message": "Record number is required."},
            )
            return

        if not report_date:
            self._write_json(
                HTTPStatus.BAD_REQUEST,
                {"success": False, "code": "VALIDATION_ERROR", "message": "Report date is required."},
            )
            return

        if not institution:
            self._write_json(
                HTTPStatus.BAD_REQUEST,
                {"success": False, "code": "VALIDATION_ERROR", "message": "Medical institution is required."},
            )
            return

        if not file_name:
            self._write_json(
                HTTPStatus.BAD_REQUEST,
                {"success": False, "code": "VALIDATION_ERROR", "message": "Medical report file is required."},
            )
            return

        try:
            file_bytes = decode_pdf_base64(file_base64)
            parsed_result = MedicalReportConnectorAdapter(
                provider=provider,
                model_id=model_id,
                api_key=api_key,
            ).parse_report(
                record_number=record_number,
                report_date=report_date,
                institution=institution,
                file_name=file_name,
                file_bytes=file_bytes,
            )
        except MedicalReportConnectorError as error:
            self._write_json(
                HTTPStatus(error.http_status),
                {"success": False, "code": error.code, "message": error.message},
            )
            return

        self._write_json(HTTPStatus.OK, {"success": True, "code": "REPORT_PARSED", "message": "Medical report parsed.", "data": parsed_result})


def main() -> None:
    logging.basicConfig(level=logging.INFO)
    server = ThreadingHTTPServer(("0.0.0.0", 8081), InternalApiHandler)
    logger.info("starting sync-worker internal API on :8081")
    server.serve_forever()


if __name__ == "__main__":
    main()

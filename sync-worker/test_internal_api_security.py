from __future__ import annotations

import os
import unittest
from http import HTTPStatus
from unittest.mock import Mock

os.environ.setdefault("CONNECTOR_SECRET_KEY", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
os.environ.setdefault("INTERNAL_API_TOKEN", "unit-test-internal-token-123456")

from sync_worker.connectors.medical_report import MedicalReportConnectorError, _raise_provider_http_error
from sync_worker.internal_api import InternalApiHandler


class _FakeResponse:
    def __init__(self, status_code: int) -> None:
        self.status_code = status_code


class InternalApiSecurityTest(unittest.TestCase):
    def test_internal_endpoints_require_internal_token(self) -> None:
        handler = InternalApiHandler.__new__(InternalApiHandler)
        handler.path = "/internal/connectors/garmin-connect/verify"
        handler.headers = {}
        handler._read_json = Mock(side_effect=AssertionError("request body should not be read without a valid token"))
        captured: dict[str, object] = {}
        handler._write_json = lambda status, payload: captured.update(status=status, payload=payload)

        handler.do_POST()

        handler._read_json.assert_not_called()
        self.assertEqual(HTTPStatus.FORBIDDEN, captured["status"])
        self.assertEqual("FORBIDDEN", captured["payload"]["code"])


class MedicalReportProviderErrorMappingTest(unittest.TestCase):
    def test_provider_auth_errors_map_to_safe_connector_error(self) -> None:
        with self.assertRaises(MedicalReportConnectorError) as error:
            _raise_provider_http_error(_FakeResponse(401), parse_message="raw provider message")

        self.assertEqual("MODEL_AUTH_FAILED", error.exception.code)
        self.assertEqual("Provider authentication failed.", error.exception.message)
        self.assertEqual(400, error.exception.http_status)

    def test_provider_rate_limit_errors_map_to_connection_error(self) -> None:
        with self.assertRaises(MedicalReportConnectorError) as error:
            _raise_provider_http_error(_FakeResponse(429), parse_message="raw provider message")

        self.assertEqual("MODEL_CONNECTION_ERROR", error.exception.code)
        self.assertEqual("Unable to reach model provider right now.", error.exception.message)
        self.assertEqual(502, error.exception.http_status)


if __name__ == "__main__":
    unittest.main()

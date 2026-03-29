from __future__ import annotations

import base64
import binascii
import hashlib
import os
from dataclasses import dataclass

CONNECTOR_SECRET_KEY_PLACEHOLDER = "UkVQTEFDRV9XSVRIXzMyX0JZVEVfQUVTX0tFWV9OT1c="
PUBLISHED_CONNECTOR_SECRET_KEY_SHA256 = "97f52fab4e515dc1d849dc2ecbb77f65e8ca1dcdb8588afa57168e9bcd4137ed"
INTERNAL_API_TOKEN_PLACEHOLDER = "REPLACE_WITH_A_RANDOM_INTERNAL_API_TOKEN_NOW"
PUBLISHED_INTERNAL_API_TOKEN_SHA256 = "2a44e4449440bc59034a53b62e4bb5838f881f06e84e22a52201f80bc19e24df"


def _sha256_hex(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


@dataclass(frozen=True)
class Settings:
    db_url: str
    celery_broker_url: str
    celery_result_backend: str
    dbt_runner_url: str
    admin_api_internal_base_url: str
    connector_secret_key: bytes
    internal_api_token: str
    app_timezone: str
    garmin_mock_mode: bool
    scheduler_poll_seconds: int

    @classmethod
    def from_env(cls) -> "Settings":
        connector_secret_key_value = os.getenv("CONNECTOR_SECRET_KEY", CONNECTOR_SECRET_KEY_PLACEHOLDER)
        internal_api_token = os.getenv("INTERNAL_API_TOKEN", INTERNAL_API_TOKEN_PLACEHOLDER)

        if len(internal_api_token.strip()) < 24:
            raise ValueError("INTERNAL_API_TOKEN must be configured with a value of at least 24 characters.")
        if internal_api_token == INTERNAL_API_TOKEN_PLACEHOLDER or _sha256_hex(internal_api_token) == PUBLISHED_INTERNAL_API_TOKEN_SHA256:
            raise ValueError("INTERNAL_API_TOKEN must not reuse the tracked placeholder or the published default value.")

        if not connector_secret_key_value.strip():
            raise ValueError("CONNECTOR_SECRET_KEY must be configured with a base64-encoded 32-byte AES key.")
        if (
            connector_secret_key_value == CONNECTOR_SECRET_KEY_PLACEHOLDER
            or _sha256_hex(connector_secret_key_value) == PUBLISHED_CONNECTOR_SECRET_KEY_SHA256
        ):
            raise ValueError("CONNECTOR_SECRET_KEY must not reuse the tracked placeholder or the published default value.")

        try:
            decoded_connector_secret_key = base64.b64decode(connector_secret_key_value)
        except (ValueError, binascii.Error) as error:
            raise ValueError("CONNECTOR_SECRET_KEY must be valid base64 and decode to 32 bytes.") from error

        if len(decoded_connector_secret_key) != 32:
            raise ValueError("CONNECTOR_SECRET_KEY must decode to exactly 32 bytes.")

        return cls(
            db_url=os.getenv("DB_URL", "postgresql://postgres:postgres@localhost:5432/self_management"),
            celery_broker_url=os.getenv("CELERY_BROKER_URL", "redis://redis:6379/0"),
            celery_result_backend=os.getenv("CELERY_RESULT_BACKEND", "redis://redis:6379/1"),
            dbt_runner_url=os.getenv("DBT_RUNNER_URL", "http://dbt-runner:8090"),
            admin_api_internal_base_url=os.getenv("ADMIN_API_INTERNAL_BASE_URL", "http://admin-api:8080"),
            connector_secret_key=decoded_connector_secret_key,
            internal_api_token=internal_api_token,
            app_timezone=os.getenv("APP_TIMEZONE", "Asia/Shanghai"),
            garmin_mock_mode=os.getenv("GARMIN_MOCK_MODE", "false").lower() == "true",
            scheduler_poll_seconds=int(os.getenv("SCHEDULER_POLL_SECONDS", "15")),
        )


settings = Settings.from_env()

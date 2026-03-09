from __future__ import annotations

import base64
import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    db_url: str
    celery_broker_url: str
    celery_result_backend: str
    dbt_runner_url: str
    connector_secret_key: bytes
    app_timezone: str
    garmin_mock_mode: bool
    scheduler_poll_seconds: int

    @classmethod
    def from_env(cls) -> "Settings":
        return cls(
            db_url=os.getenv("DB_URL", "postgresql://postgres:postgres@localhost:5432/self_management"),
            celery_broker_url=os.getenv("CELERY_BROKER_URL", os.getenv("REDIS_URL", "redis://localhost:6379/0")),
            celery_result_backend=os.getenv("CELERY_RESULT_BACKEND", os.getenv("REDIS_URL", "redis://localhost:6379/1")),
            dbt_runner_url=os.getenv("DBT_RUNNER_URL", "http://dbt-runner:8090"),
            connector_secret_key=base64.b64decode(os.getenv("CONNECTOR_SECRET_KEY", "0jLUxjwhATZYPdYIgyqCzherzo2VFFa6qOdO1vMpZPI=")),
            app_timezone=os.getenv("APP_TIMEZONE", "Asia/Shanghai"),
            garmin_mock_mode=os.getenv("GARMIN_MOCK_MODE", "true").lower() == "true",
            scheduler_poll_seconds=int(os.getenv("SCHEDULER_POLL_SECONDS", "15")),
        )


settings = Settings.from_env()

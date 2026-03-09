from __future__ import annotations

from abc import ABC, abstractmethod
from datetime import datetime
from typing import Any


class BaseConnectorAdapter(ABC):
    @abstractmethod
    def verify_connection(self) -> None:
        raise NotImplementedError

    @abstractmethod
    def fetch_profile(self) -> dict[str, Any]:
        raise NotImplementedError

    @abstractmethod
    def fetch_daily_summaries(self, start_at: datetime, end_at: datetime) -> list[dict[str, Any]]:
        raise NotImplementedError

    @abstractmethod
    def fetch_activities(self, start_at: datetime, end_at: datetime) -> list[dict[str, Any]]:
        raise NotImplementedError

    @abstractmethod
    def fetch_sleep_sessions(self, start_at: datetime, end_at: datetime) -> list[dict[str, Any]]:
        raise NotImplementedError

    @abstractmethod
    def fetch_heart_rates(self, start_at: datetime, end_at: datetime) -> list[dict[str, Any]]:
        raise NotImplementedError

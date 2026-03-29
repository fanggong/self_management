from __future__ import annotations

from datetime import date, datetime, timedelta, timezone
from typing import Any, Iterable

from zoneinfo import ZoneInfo

from .base import BaseConnectorAdapter
from ..config import settings

AUTH_ERROR_TOKENS = ("credential", "password", "username", "auth", "login", "401", "403", "forbidden", "unauthorized")
CONNECTION_ERROR_TOKENS = ("timeout", "connection", "network", "proxy", "temporarily", "rate", "unavailable", "bad gateway", "503")


def sanitize_garmin_sync_error(error: Exception) -> tuple[str, str]:
    message = str(error).strip().lower()

    if any(token in message for token in AUTH_ERROR_TOKENS):
        return (
            "CONNECTOR_AUTH_FAILED",
            "Garmin Connect authentication failed. Update the stored username or password.",
        )

    if any(token in message for token in CONNECTION_ERROR_TOKENS):
        return (
            "CONNECTOR_CONNECTION_ERROR",
            "Unable to reach Garmin Connect right now. Please try again later.",
        )

    return ("SYNC_EXECUTION_FAILED", "Garmin Connect sync failed.")


class GarminConnectorAdapter(BaseConnectorAdapter):
    def __init__(self, username: str, password: str) -> None:
        self.username = username
        self.password = password
        self._client: Any | None = None
        self._local_timezone = ZoneInfo(settings.app_timezone)

    def verify_connection(self) -> None:
        if settings.garmin_mock_mode:
            if len(self.password) < 6 or not self.username.strip():
                raise ValueError("Garmin credentials are invalid.")
            return

        self._get_client()

    def fetch_profile(self) -> dict[str, Any]:
        local_today = self._local_today()

        if settings.garmin_mock_mode:
            payload = {
                "displayName": self.username.split("@")[0] if "@" in self.username else self.username,
                "provider": "garmin-connect",
                "region": "cn",
                "heightCm": 172.0,
                "userSettings": {
                    "userData": {
                        "height": 172,
                        "measurementSystem": "metric",
                    }
                },
                "profileSettings": {},
            }
            return {
                "externalId": self.username,
                "sourceRecordDate": local_today.isoformat(),
                "sourceRecordAt": None,
                "sourceUpdatedAt": None,
                "payload": payload,
            }

        payload = self._fetch_current_profile_payload()
        return {
            "externalId": str(
                payload.get("externalId")
                or payload.get("profileId")
                or payload.get("id")
                or payload.get("garminGUID")
                or self.username
            ),
            "sourceRecordDate": local_today.isoformat(),
            "sourceRecordAt": None,
            "sourceUpdatedAt": None,
            "payload": payload,
        }

    def fetch_daily_summaries(self, start_at: datetime, end_at: datetime) -> list[dict[str, Any]]:
        rows: list[dict[str, Any]] = []
        if settings.garmin_mock_mode:
            for cursor in self._iter_days(start_at, end_at):
                rows.append(
                    {
                        "externalId": f"{self.username}:{cursor.isoformat()}",
                        "sourceRecordDate": cursor.isoformat(),
                        "sourceRecordAt": None,
                        "sourceUpdatedAt": None,
                        "payload": {
                            "totalSteps": 10000,
                            "totalDistanceMeters": 7600.0,
                            "totalKilocalories": 510.0,
                            "moderateIntensityMinutes": 68,
                        },
                    }
                )
            return rows

        client = self._get_client()
        for cursor in self._iter_days(start_at, end_at):
            rows.append(
                {
                    "externalId": f"{self.username}:{cursor.isoformat()}",
                    "sourceRecordDate": cursor.isoformat(),
                    "sourceRecordAt": None,
                    "sourceUpdatedAt": None,
                    "payload": client.get_stats(cursor.isoformat()),
                }
            )
        return rows

    def fetch_body_compositions(self, start_at: datetime, end_at: datetime) -> list[dict[str, Any]]:
        rows: list[dict[str, Any]] = []
        if settings.garmin_mock_mode:
            for index, cursor in enumerate(self._iter_days(start_at, end_at)):
                measurement_local = datetime.combine(cursor, datetime.min.time(), tzinfo=self._local_timezone) + timedelta(hours=7, minutes=15)
                weight = 68.2 + (index * 0.15)
                bmi = 22.4 + (index * 0.05)
                payload = {
                    "dateWeightList": [
                        {
                            "date": cursor.isoformat(),
                            "weight": round(weight, 2),
                            "bmi": round(bmi, 2),
                            "samplePk": int(measurement_local.timestamp() * 1000),
                            "measurementTimeGMT": measurement_local.astimezone(timezone.utc).isoformat(),
                            "measurementTimeLocal": measurement_local.isoformat(),
                        }
                    ],
                    "totalAverage": {
                        "weight": round(weight, 2),
                        "bmi": round(bmi, 2),
                    },
                }
                rows.append(
                    {
                        "externalId": f"body:{self.username}:{cursor.isoformat()}",
                        "sourceRecordDate": cursor.isoformat(),
                        "sourceRecordAt": measurement_local.astimezone(timezone.utc),
                        "sourceUpdatedAt": measurement_local.astimezone(timezone.utc),
                        "payload": payload,
                    }
                )
            return rows

        client = self._get_client()
        for cursor in self._iter_days(start_at, end_at):
            payload = client.get_body_composition(cursor.isoformat())
            if not isinstance(payload, dict) or self._is_empty_body_composition(payload):
                continue

            measurement = self._extract_body_composition_entry(payload)
            source_record_date = (
                self._extract_body_composition_date(measurement)
                or cursor.isoformat()
            )
            source_record_at = self._extract_body_composition_timestamp(measurement)
            rows.append(
                {
                    "externalId": f"body:{self.username}:{source_record_date}",
                    "sourceRecordDate": source_record_date,
                    "sourceRecordAt": source_record_at,
                    "sourceUpdatedAt": source_record_at,
                    "payload": payload,
                }
            )
        return rows

    def fetch_activities(self, start_at: datetime, end_at: datetime) -> list[dict[str, Any]]:
        rows: list[dict[str, Any]] = []
        if settings.garmin_mock_mode:
            for index, cursor in enumerate(self._iter_days(start_at, end_at)):
                if index % 3 != 0:
                    continue
                start_time = datetime.combine(cursor, datetime.min.time(), tzinfo=self._local_timezone) + timedelta(hours=6, minutes=30)
                payload = {
                    "activityId": f"{cursor.strftime('%Y%m%d')}{index:02d}",
                    "activityName": "Morning Run",
                    "activityType": {"typeKey": "running"},
                    "startTimeGMT": start_time.astimezone(timezone.utc).isoformat(),
                    "startTimeLocal": start_time.isoformat(),
                    "duration": 2700,
                    "distance": 6200.0,
                    "calories": 460.0,
                    "averageHR": 146,
                    "maxHR": 172,
                    "steps": 7800,
                }
                rows.append(
                    {
                        "externalId": str(payload["activityId"]),
                        "sourceRecordDate": cursor.isoformat(),
                        "sourceRecordAt": start_time.astimezone(timezone.utc),
                        "sourceUpdatedAt": None,
                        "payload": payload,
                    }
                )
            return rows

        for index, payload in enumerate(self._get_client().get_activities_by_date(start_at.date().isoformat(), end_at.date().isoformat())):
            activity_id = payload.get("activityId") or payload.get("activityUUID") or payload.get("summaryId")
            start_local = self._parse_timestamp(payload.get("startTimeLocal"), default_tz=self._local_timezone)
            start_gmt = self._parse_timestamp(payload.get("startTimeGMT"), default_tz=timezone.utc)
            start_value = start_gmt or start_local
            source_record_date = (
                start_value.astimezone(self._local_timezone).date().isoformat()
                if start_value is not None
                else start_at.astimezone(self._local_timezone).date().isoformat()
            )
            rows.append(
                {
                    "externalId": str(activity_id or f"activity:{self.username}:{source_record_date}:{index}"),
                    "sourceRecordDate": source_record_date,
                    "sourceRecordAt": start_value,
                    "sourceUpdatedAt": self._parse_timestamp(payload.get("updateDate"), default_tz=self._local_timezone),
                    "payload": payload,
                }
            )
        return rows

    def fetch_sleep_sessions(self, start_at: datetime, end_at: datetime) -> list[dict[str, Any]]:
        rows: list[dict[str, Any]] = []
        if settings.garmin_mock_mode:
            for cursor in self._iter_days(start_at, end_at):
                start_time = datetime.combine(cursor, datetime.min.time(), tzinfo=self._local_timezone) + timedelta(hours=23)
                end_time = start_time + timedelta(hours=7, minutes=20)
                payload = {
                    "dailySleepDTO": {
                        "id": f"sleep:{cursor.isoformat()}",
                        "calendarDate": cursor.isoformat(),
                        "sleepStartTimestampGMT": start_time.astimezone(timezone.utc).isoformat(),
                        "sleepEndTimestampGMT": end_time.astimezone(timezone.utc).isoformat(),
                        "sleepTimeSeconds": 7 * 3600,
                        "napTimeSeconds": 0,
                        "deepSleepSeconds": 5400,
                        "lightSleepSeconds": 12600,
                        "remSleepSeconds": 7200,
                        "awakeSleepSeconds": 1200,
                    },
                    "sleepLevels": [],
                    "sleepMovement": [],
                }
                rows.append(
                    {
                        "externalId": f"sleep:{self.username}:{cursor.isoformat()}",
                        "sourceRecordDate": cursor.isoformat(),
                        "sourceRecordAt": start_time.astimezone(timezone.utc),
                        "sourceUpdatedAt": end_time.astimezone(timezone.utc),
                        "payload": payload,
                    }
                )
            return rows

        client = self._get_client()
        for cursor in self._iter_days(start_at, end_at):
            payload = client.get_sleep_data(cursor.isoformat())
            dto = payload.get("dailySleepDTO") if isinstance(payload, dict) else None
            if not isinstance(dto, dict) or self._is_empty_sleep(dto):
                continue
            source_record_date = dto.get("calendarDate") or cursor.isoformat()
            rows.append(
                {
                    "externalId": str(dto.get("id") or f"sleep:{self.username}:{source_record_date}"),
                    "sourceRecordDate": source_record_date,
                    "sourceRecordAt": self._parse_timestamp(dto.get("sleepStartTimestampGMT"), default_tz=timezone.utc),
                    "sourceUpdatedAt": self._parse_timestamp(dto.get("sleepEndTimestampGMT"), default_tz=timezone.utc),
                    "payload": payload,
                }
            )
        return rows

    def fetch_heart_rates(self, start_at: datetime, end_at: datetime) -> list[dict[str, Any]]:
        rows: list[dict[str, Any]] = []
        if settings.garmin_mock_mode:
            for index, cursor in enumerate(self._iter_days(start_at, end_at)):
                start_time = datetime.combine(cursor, datetime.min.time(), tzinfo=timezone.utc)
                payload = {
                    "calendarDate": cursor.isoformat(),
                    "startTimestampGMT": start_time.isoformat(),
                    "endTimestampGMT": (start_time + timedelta(days=1)).isoformat(),
                    "restingHeartRate": 56,
                    "minHeartRate": 49,
                    "maxHeartRate": 154,
                    "heartRateValueDescriptors": [
                        {"index": 0, "key": "timestamp"},
                        {"index": 1, "key": "heartrate"},
                    ],
                    "heartRateValues": [
                        [int(start_time.timestamp() * 1000), 68 + (index % 4)],
                        [int((start_time + timedelta(minutes=5)).timestamp() * 1000), 70 + (index % 3)],
                    ],
                }
                rows.append(
                    {
                        "externalId": f"hr:{self.username}:{cursor.isoformat()}",
                        "sourceRecordDate": cursor.isoformat(),
                        "sourceRecordAt": start_time,
                        "sourceUpdatedAt": start_time + timedelta(days=1),
                        "payload": payload,
                    }
                )
            return rows

        client = self._get_client()
        for cursor in self._iter_days(start_at, end_at):
            payload = client.get_heart_rates(cursor.isoformat())
            if not isinstance(payload, dict) or self._is_empty_heart_rate(payload):
                continue
            source_record_date = payload.get("calendarDate") or cursor.isoformat()
            rows.append(
                {
                    "externalId": f"hr:{self.username}:{source_record_date}",
                    "sourceRecordDate": source_record_date,
                    "sourceRecordAt": self._parse_timestamp(payload.get("startTimestampGMT"), default_tz=timezone.utc),
                    "sourceUpdatedAt": self._parse_timestamp(payload.get("endTimestampGMT"), default_tz=timezone.utc),
                    "payload": payload,
                }
            )
        return rows

    def _get_client(self) -> Any:
        if self._client is None:
            from garminconnect import Garmin  # type: ignore

            self._client = Garmin(email=self.username, password=self.password, is_cn=True)
            self._client.login()
        return self._client

    def _fetch_current_profile_payload(self) -> dict[str, Any]:
        public_profile = self._fetch_public_profile_payload()
        user_settings = self._fetch_user_settings_payload()
        profile_settings = self._fetch_profile_settings_payload()

        merged_payload = dict(public_profile)
        merged_payload["userSettings"] = user_settings
        merged_payload["profileSettings"] = profile_settings
        merged_payload["heightCm"] = self._extract_height_cm(user_settings, profile_settings)
        return merged_payload

    def _fetch_public_profile_payload(self) -> dict[str, Any]:
        client = self._get_client()
        garth_client = getattr(client, "garth", None)
        profile = getattr(garth_client, "profile", None) if garth_client is not None else None
        if isinstance(profile, dict) and profile:
            return profile

        payload = self._fetch_connectapi_payload("/userprofile-service/userprofile/profile")
        if payload:
            return payload

        raise ValueError("Garmin current profile payload is unavailable after login.")

    def _fetch_user_settings_payload(self) -> dict[str, Any]:
        client = self._get_client()
        get_user_profile = getattr(client, "get_user_profile", None)
        if callable(get_user_profile):
            try:
                payload = get_user_profile()
                if isinstance(payload, dict) and payload:
                    return payload
            except Exception:
                pass

        payload = self._fetch_connectapi_payload("/userprofile-service/userprofile/user-settings")
        return payload or {}

    def _fetch_profile_settings_payload(self) -> dict[str, Any]:
        client = self._get_client()
        get_userprofile_settings = getattr(client, "get_userprofile_settings", None)
        if callable(get_userprofile_settings):
            try:
                payload = get_userprofile_settings()
                if isinstance(payload, dict) and payload:
                    return payload
            except Exception:
                pass

        payload = self._fetch_connectapi_payload("/userprofile-service/userprofile/settings")
        return payload or {}

    def _fetch_connectapi_payload(self, path: str) -> dict[str, Any] | None:
        client = self._get_client()
        garth_client = getattr(client, "garth", None)
        connectapi = getattr(garth_client, "connectapi", None) if garth_client is not None else None
        if callable(connectapi):
            try:
                payload = connectapi(path)
            except Exception:
                return None
            if isinstance(payload, dict) and payload:
                return payload
        return None

    def _iter_days(self, start_at: datetime, end_at: datetime) -> Iterable[date]:
        cursor = start_at.astimezone(self._local_timezone).date()
        last_day = end_at.astimezone(self._local_timezone).date()
        while cursor <= last_day:
            yield cursor
            cursor += timedelta(days=1)

    def _local_today(self) -> date:
        return datetime.now(self._local_timezone).date()

    @staticmethod
    def _coerce_int(value: Any) -> int | None:
        if value is None or value == "":
            return None
        if isinstance(value, bool):
            return int(value)
        if isinstance(value, (int, float)):
            return int(value)
        try:
            return int(float(str(value)))
        except (TypeError, ValueError):
            return None

    @staticmethod
    def _coerce_float(value: Any) -> float | None:
        if value is None or value == "":
            return None
        if isinstance(value, bool):
            return float(value)
        if isinstance(value, (int, float)):
            return float(value)
        try:
            return float(str(value))
        except (TypeError, ValueError):
            return None

    def _parse_timestamp(self, value: Any, default_tz: timezone | ZoneInfo) -> datetime | None:
        if value in (None, ""):
            return None
        if isinstance(value, datetime):
            return value if value.tzinfo is not None else value.replace(tzinfo=default_tz)
        if isinstance(value, (int, float)):
            raw = float(value)
            if raw > 10_000_000_000:
                raw /= 1000.0
            return datetime.fromtimestamp(raw, tz=timezone.utc)
        if isinstance(value, str):
            text = value.strip()
            if not text:
                return None
            candidates = [
                text.replace("Z", "+00:00"),
                text.replace(" UTC", "+00:00"),
            ]
            for candidate in candidates:
                try:
                    parsed = datetime.fromisoformat(candidate)
                    return parsed if parsed.tzinfo is not None else parsed.replace(tzinfo=default_tz)
                except ValueError:
                    continue
            for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M", "%Y/%m/%d %H:%M:%S"):
                try:
                    parsed = datetime.strptime(text, fmt)
                    return parsed.replace(tzinfo=default_tz)
                except ValueError:
                    continue
        return None

    @classmethod
    def _flatten_numeric_values(cls, value: Any) -> Iterable[float]:
        if value is None:
            return []
        if isinstance(value, dict):
            numbers: list[float] = []
            for item in value.values():
                numbers.extend(cls._flatten_numeric_values(item))
            return numbers
        if isinstance(value, list):
            numbers: list[float] = []
            for item in value:
                numbers.extend(cls._flatten_numeric_values(item))
            return numbers
        if isinstance(value, (int, float)) and not isinstance(value, bool):
            return [float(value)]
        return []

    @classmethod
    def _extract_height_cm(cls, user_settings: dict[str, Any], profile_settings: dict[str, Any]) -> float | None:
        candidates = (
            cls._extract_height_candidate(user_settings.get("userData") if isinstance(user_settings.get("userData"), dict) else None, user_settings),
            cls._extract_height_candidate(user_settings, user_settings),
            cls._extract_height_candidate(profile_settings.get("userData") if isinstance(profile_settings.get("userData"), dict) else None, profile_settings),
            cls._extract_height_candidate(profile_settings, profile_settings),
        )
        for value, unit_hint in candidates:
            normalized = cls._normalize_height_cm(value, unit_hint)
            if normalized is not None:
                return normalized
        return None

    @classmethod
    def _extract_height_candidate(
        cls,
        payload: dict[str, Any] | None,
        context: dict[str, Any] | None,
    ) -> tuple[Any, str | None]:
        if not isinstance(payload, dict):
            return None, None

        for key in ("heightCm", "heightCM", "heightInCm", "heightInCentimeters", "userHeightCm"):
            if key in payload:
                return payload.get(key), "cm"

        if "height" in payload:
            return payload.get("height"), cls._extract_height_unit_hint(payload, context)

        for key in ("heightMeters", "heightInMeters"):
            if key in payload:
                return payload.get(key), "m"

        for key in ("heightInInches", "heightInIn"):
            if key in payload:
                return payload.get(key), "in"

        return None, cls._extract_height_unit_hint(payload, context)

    @staticmethod
    def _extract_height_unit_hint(payload: dict[str, Any] | None, context: dict[str, Any] | None) -> str | None:
        search_order: list[dict[str, Any]] = []
        if isinstance(payload, dict):
            search_order.append(payload)
        if isinstance(context, dict) and context is not payload:
            search_order.append(context)

        for source in search_order:
            for key in (
                "heightUnit",
                "heightUnits",
                "heightUom",
                "heightMeasurementUnit",
                "measurementSystem",
                "measurementUnit",
            ):
                value = source.get(key)
                if isinstance(value, str) and value.strip():
                    return value.strip().lower()
        return None

    @classmethod
    def _normalize_height_cm(cls, value: Any, unit_hint: str | None) -> float | None:
        numeric_value = cls._coerce_float(value)
        if numeric_value is None or numeric_value <= 0:
            return None

        normalized_hint = (unit_hint or "").strip().lower()
        if normalized_hint in {"cm", "centimeter", "centimeters", "metric"} and numeric_value >= 100:
            return round(numeric_value, 1)
        if normalized_hint in {"m", "meter", "meters"}:
            return round(numeric_value * 100.0, 1)
        if normalized_hint in {"in", "inch", "inches", "imperial"} and 36 <= numeric_value <= 96:
            return round(numeric_value * 2.54, 1)

        if numeric_value >= 100:
            return round(numeric_value, 1)
        if numeric_value < 3:
            return round(numeric_value * 100.0, 1)
        return None

    @staticmethod
    def _is_empty_sleep(dto: dict[str, Any]) -> bool:
        metrics = (
            dto.get("sleepTimeSeconds"),
            dto.get("deepSleepSeconds"),
            dto.get("lightSleepSeconds"),
            dto.get("remSleepSeconds"),
            dto.get("awakeSleepSeconds"),
            dto.get("sleepStartTimestampGMT"),
            dto.get("sleepEndTimestampGMT"),
        )
        return all(metric in (None, 0, "") for metric in metrics)

    def _is_empty_heart_rate(self, payload: dict[str, Any]) -> bool:
        metrics = (
            payload.get("restingHeartRate"),
            payload.get("minHeartRate"),
            payload.get("maxHeartRate"),
        )
        return all(metric in (None, 0, "") for metric in metrics) and not self._extract_heart_rate_values(payload)

    def _is_empty_body_composition(self, payload: dict[str, Any]) -> bool:
        entry = self._extract_body_composition_entry(payload)
        if entry:
            return (
                self._coerce_float(entry.get("weight")) is None
                and self._coerce_float(entry.get("weightInKg")) is None
                and self._coerce_float(entry.get("bmi")) is None
            )

        total_average = payload.get("totalAverage")
        if isinstance(total_average, dict):
            return (
                self._coerce_float(total_average.get("weight")) is None
                and self._coerce_float(total_average.get("weightInKg")) is None
                and self._coerce_float(total_average.get("bmi")) is None
            )

        return True

    @staticmethod
    def _extract_body_composition_entry(payload: dict[str, Any]) -> dict[str, Any]:
        date_weight_list = payload.get("dateWeightList")
        if isinstance(date_weight_list, list):
            for item in date_weight_list:
                if isinstance(item, dict):
                    return item

        total_average = payload.get("totalAverage")
        if isinstance(total_average, dict):
            return total_average

        daily_summaries = payload.get("dailyWeightSummaries")
        if isinstance(daily_summaries, list):
            for item in daily_summaries:
                if isinstance(item, dict):
                    return item

        return {}

    def _extract_body_composition_date(self, entry: dict[str, Any]) -> str | None:
        for key in ("date", "calendarDate", "measurementDate"):
            value = entry.get(key)
            if isinstance(value, str) and value.strip():
                return value.strip()

        sample_pk = entry.get("samplePk")
        if sample_pk is not None:
            parsed = self._parse_timestamp(sample_pk, default_tz=timezone.utc)
            if parsed is not None:
                return parsed.astimezone(self._local_timezone).date().isoformat()

        timestamp = self._extract_body_composition_timestamp(entry)
        if timestamp is not None:
            return timestamp.astimezone(self._local_timezone).date().isoformat()

        return None

    def _extract_body_composition_timestamp(self, entry: dict[str, Any]) -> datetime | None:
        timestamp_keys = (
            "measurementTimeGMT",
            "measurementTimestampGMT",
            "measurementTimeLocal",
            "measurementTimestampLocal",
            "timestampGMT",
            "timestampLocal",
            "samplePk",
        )
        for key in timestamp_keys:
            parsed = self._parse_timestamp(
                entry.get(key),
                default_tz=self._local_timezone if "Local" in key else timezone.utc,
            )
            if parsed is not None:
                return parsed
        return None

    @classmethod
    def _extract_heart_rate_values(cls, payload: dict[str, Any]) -> list[float]:
        descriptors = payload.get("heartRateValueDescriptors")
        heart_rate_index: int | None = None
        if isinstance(descriptors, list):
            for descriptor in descriptors:
                if isinstance(descriptor, dict) and str(descriptor.get("key", "")).lower() == "heartrate":
                    heart_rate_index = cls._coerce_int(descriptor.get("index"))
                    break

        raw_values = payload.get("heartRateValues")
        if isinstance(raw_values, list):
            if heart_rate_index is not None:
                extracted: list[float] = []
                for item in raw_values:
                    if isinstance(item, list) and heart_rate_index < len(item):
                        numeric = cls._coerce_float(item[heart_rate_index])
                        if numeric is not None:
                            extracted.append(numeric)
                if extracted:
                    return extracted
            return list(cls._flatten_numeric_values(raw_values))
        if isinstance(raw_values, dict):
            return list(cls._flatten_numeric_values(raw_values.values()))
        return []

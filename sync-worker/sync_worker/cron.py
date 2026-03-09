from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Callable
from zoneinfo import ZoneInfo


@dataclass(frozen=True)
class CronField:
    allowed: set[int]
    wildcard: bool


@dataclass(frozen=True)
class CronSchedule:
    minute: CronField
    hour: CronField
    day_of_month: CronField
    month: CronField
    day_of_week: CronField


def next_run(expression: str, zone_name: str, from_dt: datetime) -> datetime:
    schedule = _parse_expression(expression)
    zone = ZoneInfo(zone_name)
    candidate = from_dt.astimezone(zone).replace(second=0, microsecond=0) + timedelta(minutes=1)

    for _ in range(60 * 24 * 366):
        if _matches_candidate(schedule, candidate):
            return candidate.astimezone(timezone.utc)

        candidate += timedelta(minutes=1)

    raise ValueError("Unable to find the next run time within 366 days.")


def previous_run(expression: str, zone_name: str, from_dt: datetime) -> datetime:
    schedule = _parse_expression(expression)
    zone = ZoneInfo(zone_name)
    candidate = from_dt.astimezone(zone).replace(second=0, microsecond=0) - timedelta(minutes=1)

    for _ in range(60 * 24 * 366):
        if _matches_candidate(schedule, candidate):
            return candidate.astimezone(timezone.utc)

        candidate -= timedelta(minutes=1)

    raise ValueError("Unable to find the previous run time within 366 days.")


def _parse_expression(expression: str) -> CronSchedule:
    fields = expression.strip().split()
    if len(fields) != 5:
        raise ValueError("Cron expression must contain 5 fields.")

    return CronSchedule(
        minute=_parse_field(fields[0], 0, 59),
        hour=_parse_field(fields[1], 0, 23),
        day_of_month=_parse_field(fields[2], 1, 31),
        month=_parse_field(fields[3], 1, 12),
        day_of_week=_parse_field(fields[4], 0, 7, normalize=lambda value: 0 if value == 7 else value),
    )


def _parse_field(
    value: str,
    min_value: int,
    max_value: int,
    normalize: Callable[[int], int] | None = None,
) -> CronField:
    trimmed = value.strip()
    if not trimmed:
        raise ValueError("Each cron field must be provided.")

    allowed: set[int] = set()
    wildcard = trimmed == "*"
    if wildcard:
        for current in range(min_value, max_value + 1):
            allowed.add(normalize(current) if normalize else current)
        return CronField(allowed=allowed, wildcard=True)

    for segment in trimmed.split(","):
        range_part, *step_parts = segment.split("/")
        step = int(step_parts[0]) if step_parts else 1
        if step <= 0:
            raise ValueError(f'Invalid step value "{step_parts[0] if step_parts else step}" in "{value}".')

        start = min_value
        end = max_value
        if range_part != "*":
            boundaries = range_part.split("-")
            start = int(boundaries[0])
            end = int(boundaries[1]) if len(boundaries) == 2 else start

        if start < min_value or end > max_value or start > end:
            raise ValueError(f'Field "{value}" is outside the supported range {min_value}-{max_value}.')

        for current in range(start, end + 1, step):
            allowed.add(normalize(current) if normalize else current)

    return CronField(allowed=allowed, wildcard=False)


def _matches_day(day_of_month: CronField, day_of_week: CronField, current_day_of_month: int, current_day_of_week: int) -> bool:
    day_of_month_match = current_day_of_month in day_of_month.allowed
    day_of_week_match = current_day_of_week in day_of_week.allowed

    if day_of_month.wildcard and day_of_week.wildcard:
        return True
    if day_of_month.wildcard:
        return day_of_week_match
    if day_of_week.wildcard:
        return day_of_month_match
    return day_of_month_match or day_of_week_match


def _matches_candidate(schedule: CronSchedule, candidate: datetime) -> bool:
    current_day_of_week = (candidate.weekday() + 1) % 7
    return (
        candidate.month in schedule.month.allowed
        and candidate.hour in schedule.hour.allowed
        and candidate.minute in schedule.minute.allowed
        and _matches_day(schedule.day_of_month, schedule.day_of_week, candidate.day, current_day_of_week)
    )

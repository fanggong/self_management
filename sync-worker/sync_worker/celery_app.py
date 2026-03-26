from __future__ import annotations

from celery import Celery
from celery.schedules import crontab

from .config import settings

app = Celery(
    "sync-worker",
    broker=settings.celery_broker_url,
    backend=settings.celery_result_backend,
    include=["sync_worker.tasks.sync"],
)
app.conf.update(
    timezone=settings.app_timezone,
    enable_utc=True,
    beat_schedule={
        "dispatch-queued-sync-tasks": {
            "task": "sync.dispatch_queued_sync_tasks",
            "schedule": settings.scheduler_poll_seconds,
        },
        "run-default-marts-schedule": {
            "task": "sync.run_default_marts_schedule",
            "schedule": crontab(hour=12, minute=0),
        }
    },
)

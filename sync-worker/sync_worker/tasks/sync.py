from __future__ import annotations

from datetime import timezone

from celery.utils.log import get_task_logger

from .. import db
from ..celery_app import app
from ..cron import next_run, previous_run
from ..config import settings
from ..services.sync_executor import execute_sync_task

logger = get_task_logger(__name__)
AUTO_SYNC_LOOKBACK_RUNS = 3


def enqueue_due_scheduled_sync_tasks(limit: int = 20) -> int:
    queued = 0
    for connector in db.fetch_due_running_connectors(limit=limit):
        due_at = connector["next_run_at"]
        if due_at is None:
            continue

        if due_at.tzinfo is None:
            due_at = due_at.replace(tzinfo=timezone.utc)

        window_end_at = due_at
        window_start_at = window_end_at
        for _ in range(AUTO_SYNC_LOOKBACK_RUNS):
            window_start_at = previous_run(connector["schedule"], settings.app_timezone, window_start_at)

        task_id = db.create_scheduled_sync_task(
            connector["account_id"],
            connector["connector_config_id"],
            window_start_at,
            window_end_at,
        )
        db.set_connector_run_timestamps(
            connector["connector_config_id"],
            connector["last_run_at"],
            next_run(connector["schedule"], settings.app_timezone, due_at),
        )
        queued += 1
        logger.info("queued scheduled sync task %s for connector %s", task_id, connector["connector_config_id"])

    return queued


@app.task(name="sync.dispatch_queued_sync_tasks")
def dispatch_queued_sync_tasks() -> int:
    queued = enqueue_due_scheduled_sync_tasks(limit=20)
    dispatched = 0
    for task_id in db.fetch_queued_task_ids(limit=20):
        if db.mark_task_dispatched(task_id):
            execute_sync_task_task.delay(task_id)
            dispatched += 1
    logger.info("queued %s scheduled sync tasks and dispatched %s queued sync tasks", queued, dispatched)
    return dispatched


@app.task(name="sync.execute_sync_task", autoretry_for=(Exception,), retry_backoff=True, retry_kwargs={"max_retries": 3})
def execute_sync_task_task(task_id: str) -> None:
    logger.info("executing sync task %s", task_id)
    execute_sync_task(task_id)

package com.otw.adminapi.sync;

import java.util.UUID;

public record SyncTaskView(
  UUID jobId,
  String connectorId,
  String status,
  String triggerType,
  String startAt,
  String endAt,
  String startedAt,
  String finishedAt,
  Integer fetchedCount,
  Integer insertedCount,
  Integer updatedCount,
  Integer unchangedCount,
  Integer dedupedCount,
  String errorMessage,
  String createdAt
) {
}

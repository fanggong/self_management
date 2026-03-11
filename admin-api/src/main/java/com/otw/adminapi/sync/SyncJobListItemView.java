package com.otw.adminapi.sync;

import java.util.UUID;

public record SyncJobListItemView(
  UUID jobId,
  String connectorId,
  String connectorName,
  String domain,
  String status,
  String triggerType,
  String windowStart,
  String windowEnd,
  String startedAt,
  String finishedAt,
  Integer fetchedCount,
  Integer insertedCount,
  Integer updatedCount,
  Integer dedupedCount,
  String errorMessage,
  String createdAt
) {
}

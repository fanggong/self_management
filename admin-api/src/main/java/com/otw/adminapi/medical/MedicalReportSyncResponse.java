package com.otw.adminapi.medical;

import java.util.UUID;

public record MedicalReportSyncResponse(
  UUID jobId,
  String connectorId,
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

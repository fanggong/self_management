package com.otw.adminapi.dbt;

import java.util.UUID;

public record DbtRunHistoryListItemView(
  UUID runId,
  String modelName,
  String layer,
  String status,
  Integer returncode,
  Double executionTimeSeconds,
  String startedAt,
  String finishedAt
) {
}

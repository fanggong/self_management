package com.otw.adminapi.dbt;

import java.util.UUID;

public record DbtRunHistoryDetailView(
  UUID runId,
  String modelName,
  String layer,
  String status,
  Integer returncode,
  String startedAt,
  String finishedAt,
  String errorCode,
  String errorMessage,
  String stdout,
  String stderr
) {
}

package com.otw.adminapi.dbt;

public record DbtBatchModelRunItemView(
  String modelName,
  String layer,
  String scopeKey,
  String scopeLabel,
  boolean success,
  Integer returncode,
  String stdout,
  String stderr,
  String startedAt,
  String finishedAt,
  String code,
  String message
) {
}

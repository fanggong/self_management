package com.otw.adminapi.dbt;

public record DbtRunModelHistoryItemView(
  String modelName,
  String layer,
  String status,
  String completedAt,
  Double executionTimeSeconds
) {
}

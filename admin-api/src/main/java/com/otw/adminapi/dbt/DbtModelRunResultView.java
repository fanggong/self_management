package com.otw.adminapi.dbt;

public record DbtModelRunResultView(
  boolean success,
  int returncode,
  String stdout,
  String stderr,
  String startedAt,
  String finishedAt
) {
}

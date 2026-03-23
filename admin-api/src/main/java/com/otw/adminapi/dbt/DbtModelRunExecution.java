package com.otw.adminapi.dbt;

public record DbtModelRunExecution(
  int statusCode,
  boolean success,
  DbtModelRunResultView data,
  String message,
  String code
) {
}

package com.otw.adminapi.dbt;

public record DbtRunHistoryPageView(
  int page,
  int pageSize,
  int total,
  int totalPages
) {
}

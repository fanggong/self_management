package com.otw.adminapi.dbt;

public record DbtRunHistoryListRequest(
  Integer page,
  Integer pageSize,
  String search
) {
}

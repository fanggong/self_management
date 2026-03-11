package com.otw.adminapi.sync;

public record SyncJobListRequest(
  Integer page,
  Integer pageSize,
  String search,
  String period,
  String status,
  String triggerType,
  String domain,
  String sortBy,
  String sortOrder
) {
}

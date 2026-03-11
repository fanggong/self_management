package com.otw.adminapi.sync;

public record SyncJobListPageView(
  int page,
  int pageSize,
  int total,
  int totalPages
) {
}

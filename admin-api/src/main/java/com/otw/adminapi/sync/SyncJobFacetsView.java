package com.otw.adminapi.sync;

public record SyncJobFacetsView(
  int allTasks,
  SyncJobStatusFacets status,
  SyncJobTriggerTypeFacets triggerType,
  SyncJobDomainFacets domain,
  SyncJobPeriodFacets period
) {
  public record SyncJobStatusFacets(
    int queued,
    int running,
    int success,
    int failed
  ) {
  }

  public record SyncJobTriggerTypeFacets(
    int manual,
    int scheduled
  ) {
  }

  public record SyncJobDomainFacets(
    int health,
    int finance
  ) {
  }

  public record SyncJobPeriodFacets(
    int yesterday,
    int last7Days,
    int last30Days
  ) {
  }
}

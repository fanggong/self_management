package com.otw.adminapi.dbt;

public record DefaultScheduledMartsRunResultView(
  int processedAccounts,
  int skippedAccounts,
  int succeededModels,
  int failedModels
) {
}

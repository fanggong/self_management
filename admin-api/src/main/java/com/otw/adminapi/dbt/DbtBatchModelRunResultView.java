package com.otw.adminapi.dbt;

import java.util.List;

public record DbtBatchModelRunResultView(
  String layer,
  String scopeType,
  List<String> scopeValues,
  int totalModels,
  int succeededCount,
  int failedCount,
  List<DbtBatchModelRunItemView> items
) {
}

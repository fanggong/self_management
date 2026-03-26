package com.otw.adminapi.dbt;

import java.util.List;

public record DbtBatchModelRunResultView(
  String layer,
  String selectionType,
  List<String> modelNames,
  int totalModels,
  int succeededCount,
  int failedCount,
  List<DbtBatchModelRunItemView> items
) {
}

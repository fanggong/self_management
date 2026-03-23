package com.otw.adminapi.dbt;

import java.util.List;

public record DbtModelDetailView(
  String name,
  String layer,
  String description,
  String connector,
  String domain,
  String lastRunCompletedAt,
  Long estimatedRowCount,
  List<DbtModelColumnView> columns
) {
}

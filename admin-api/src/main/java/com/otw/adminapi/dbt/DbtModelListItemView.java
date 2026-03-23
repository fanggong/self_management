package com.otw.adminapi.dbt;

public record DbtModelListItemView(
  String name,
  String layer,
  String description,
  String connector,
  String domain,
  String connectorKey,
  String domainKey,
  String lastRunCompletedAt
) {
}

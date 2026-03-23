package com.otw.adminapi.dbt;

public record DbtModelListItemView(
  String name,
  String layer,
  String lastRunCompletedAt
) {
}

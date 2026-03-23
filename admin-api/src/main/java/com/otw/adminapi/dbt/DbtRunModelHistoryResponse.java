package com.otw.adminapi.dbt;

import java.util.List;

public record DbtRunModelHistoryResponse(
  List<DbtRunModelHistoryItemView> items
) {
}

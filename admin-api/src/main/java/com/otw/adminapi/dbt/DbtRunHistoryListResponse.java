package com.otw.adminapi.dbt;

import java.util.List;

public record DbtRunHistoryListResponse(
  List<DbtRunHistoryListItemView> items,
  DbtRunHistoryPageView page
) {
}

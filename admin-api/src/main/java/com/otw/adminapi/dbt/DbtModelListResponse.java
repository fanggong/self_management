package com.otw.adminapi.dbt;

import java.util.List;

public record DbtModelListResponse(
  List<DbtModelListItemView> items
) {
}

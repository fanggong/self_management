package com.otw.adminapi.health;

import java.util.List;

public record HealthActivityListResponse(
  List<HealthActivityListItemView> items,
  HealthActivityPageView page
) {
}

package com.otw.adminapi.health;

public record HealthActivityPageView(
  int page,
  int pageSize,
  int total,
  int totalPages
) {
}

package com.otw.adminapi.health;

public record HealthCardResponseView<T>(
  String date,
  T data
) {
}

package com.otw.adminapi.health;

public record HealthHeartRateCardView(
  Integer highest,
  Integer resting,
  Integer average
) {
}

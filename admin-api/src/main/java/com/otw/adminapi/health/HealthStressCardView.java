package com.otw.adminapi.health;

import java.math.BigDecimal;

public record HealthStressCardView(
  BigDecimal overall,
  Long lowDurationSeconds,
  Long mediumDurationSeconds,
  Long highDurationSeconds,
  Long restDurationSeconds
) {
}

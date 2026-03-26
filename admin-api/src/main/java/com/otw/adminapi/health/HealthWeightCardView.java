package com.otw.adminapi.health;

import java.math.BigDecimal;

public record HealthWeightCardView(
  BigDecimal weightKg,
  BigDecimal bmi,
  BigDecimal previousWeightKg,
  BigDecimal weightDeltaKg,
  BigDecimal weightDeltaPercent
) {
}

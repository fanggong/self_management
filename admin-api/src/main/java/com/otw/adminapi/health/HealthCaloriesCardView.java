package com.otw.adminapi.health;

import java.math.BigDecimal;

public record HealthCaloriesCardView(
  BigDecimal restingBurn,
  BigDecimal activeBurn
) {
}

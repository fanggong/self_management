package com.otw.adminapi.health;

public record HealthDashboardSummaryView(
  String summaryDate,
  HealthHeartRateCardView heartRate,
  HealthWeightCardView weight,
  HealthCaloriesCardView calories,
  HealthStressCardView stress
) {
}

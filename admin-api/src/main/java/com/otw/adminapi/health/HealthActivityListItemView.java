package com.otw.adminapi.health;

import java.math.BigDecimal;
import java.util.UUID;

public record HealthActivityListItemView(
  UUID activityRecordId,
  String activityName,
  String activityType,
  String startTime,
  String endTime,
  BigDecimal durationSeconds,
  BigDecimal calories,
  Integer avgHeartRate,
  Integer maxHeartRate
) {
}

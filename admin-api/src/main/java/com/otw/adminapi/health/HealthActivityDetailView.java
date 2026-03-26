package com.otw.adminapi.health;

import java.util.List;
import java.util.UUID;

public record HealthActivityDetailView(
  UUID activityRecordId,
  String activityName,
  String activityType,
  List<HealthDetailFieldView> basics,
  List<HealthDetailFieldView> performance,
  List<HealthDetailFieldView> heartRate,
  List<HealthDetailFieldView> locationAndTiming
) {
}

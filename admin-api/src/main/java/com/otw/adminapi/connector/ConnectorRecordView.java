package com.otw.adminapi.connector;

import java.util.Map;
import java.util.UUID;

public record ConnectorRecordView(
  UUID configId,
  String id,
  String name,
  String category,
  String status,
  String schedule,
  String lastRun,
  String nextRun,
  Map<String, String> config,
  Map<String, Boolean> secretFieldsConfigured
) {
}

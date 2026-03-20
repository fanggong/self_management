package com.otw.adminapi.medical;

import java.util.List;
import java.util.UUID;

public record MedicalReportParseResponse(
  UUID parseSessionId,
  String connectorId,
  String provider,
  String modelId,
  String parsedAt,
  List<SectionView> sections
) {
  public record SectionView(
    String sectionKey,
    String examiner,
    String examDate,
    List<ItemView> items
  ) {
  }

  public record ItemView(
    String itemKey,
    String result,
    String referenceValue,
    String unit,
    String abnormalFlag
  ) {
  }
}

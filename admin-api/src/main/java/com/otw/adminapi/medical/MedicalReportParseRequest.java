package com.otw.adminapi.medical;

public record MedicalReportParseRequest(
  String recordNumber,
  String reportDate,
  String institution
) {
}

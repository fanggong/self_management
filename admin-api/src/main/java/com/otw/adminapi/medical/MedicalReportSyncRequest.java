package com.otw.adminapi.medical;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record MedicalReportSyncRequest(
  @NotBlank String parseSessionId,
  @NotBlank String recordNumber,
  @NotBlank String reportDate,
  @NotBlank String institution,
  @NotBlank String fileName,
  @Valid MedicalReportFormInput form,
  @Valid List<MedicalReportSectionInput> sections
) {
  public record MedicalReportFormInput(
    @NotBlank String examiner,
    @NotBlank String examDate
  ) {
  }

  public record MedicalReportSectionInput(
    @NotBlank String sectionKey,
    @Valid List<MedicalReportItemInput> items
  ) {
  }

  public record MedicalReportItemInput(
    @NotBlank String itemKey,
    String result,
    String referenceValue,
    String unit,
    String abnormalFlag
  ) {
  }
}

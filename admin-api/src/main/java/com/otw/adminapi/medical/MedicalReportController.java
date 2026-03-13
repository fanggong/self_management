package com.otw.adminapi.medical;

import com.otw.adminapi.common.api.ApiResult;
import com.otw.adminapi.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users/me/connectors/medical-report")
public class MedicalReportController {
  private final MedicalReportService medicalReportService;
  private final JwtService jwtService;

  public MedicalReportController(MedicalReportService medicalReportService, JwtService jwtService) {
    this.medicalReportService = medicalReportService;
    this.jwtService = jwtService;
  }

  @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResult<MedicalReportParseResponse> parse(
    @AuthenticationPrincipal Jwt jwt,
    @RequestPart(value = "metadata", required = false) MedicalReportParseRequest metadata,
    @RequestPart(value = "recordNumber", required = false) String legacyRecordNumber,
    @RequestPart(value = "reportDate", required = false) String legacyReportDate,
    @RequestPart(value = "institution", required = false) String legacyInstitution,
    @RequestPart("file") MultipartFile file
  ) {
    var authenticatedUser = jwtService.toAuthenticatedUser(jwt);
    String recordNumber = firstNonBlank(metadata == null ? null : metadata.recordNumber(), legacyRecordNumber);
    String reportDate = firstNonBlank(metadata == null ? null : metadata.reportDate(), legacyReportDate);
    String institution = firstNonBlank(metadata == null ? null : metadata.institution(), legacyInstitution);

    MedicalReportParseResponse data = medicalReportService.parseReport(
      authenticatedUser,
      recordNumber,
      reportDate,
      institution,
      file
    );
    return ApiResult.success(data, "Medical report parsed successfully.");
  }

  private String firstNonBlank(String primary, String fallback) {
    if (primary != null && !primary.trim().isBlank()) {
      return primary;
    }
    return fallback;
  }

  @PostMapping("/sync-jobs")
  public ApiResult<MedicalReportSyncResponse> sync(
    @AuthenticationPrincipal Jwt jwt,
    @Valid @RequestBody MedicalReportSyncRequest request
  ) {
    MedicalReportSyncResponse data = medicalReportService.syncReport(jwtService.toAuthenticatedUser(jwt), request);
    return ApiResult.success(data, "Medical report synced successfully.");
  }
}

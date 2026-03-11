package com.otw.adminapi.sync;

import com.otw.adminapi.common.api.ApiResult;
import com.otw.adminapi.security.JwtService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class SyncJobController {
  private final SyncTaskService syncTaskService;
  private final JwtService jwtService;

  public SyncJobController(SyncTaskService syncTaskService, JwtService jwtService) {
    this.syncTaskService = syncTaskService;
    this.jwtService = jwtService;
  }

  @PostMapping("/connectors/{connectorId}/sync-jobs")
  public ApiResult<SyncTaskView> createSyncJob(
    @AuthenticationPrincipal Jwt jwt,
    @PathVariable String connectorId,
    @Valid @RequestBody CreateSyncJobRequest request
  ) {
    SyncTaskView data = syncTaskService.createManualSyncJob(jwtService.toAuthenticatedUser(jwt), connectorId, request);
    return ApiResult.success(data, "Garmin Connect sync job queued.");
  }

  @GetMapping("/sync-jobs")
  public ApiResult<SyncJobListResponse> listJobs(
    @AuthenticationPrincipal Jwt jwt,
    @RequestParam(defaultValue = "1") Integer page,
    @RequestParam(defaultValue = "20") Integer pageSize,
    @RequestParam(required = false) String search,
    @RequestParam(required = false) String period,
    @RequestParam(required = false) String status,
    @RequestParam(required = false) String triggerType,
    @RequestParam(required = false) String domain,
    @RequestParam(defaultValue = "createdAt") String sortBy,
    @RequestParam(defaultValue = "desc") String sortOrder
  ) {
    SyncJobListRequest request = new SyncJobListRequest(page, pageSize, search, period, status, triggerType, domain, sortBy, sortOrder);
    return ApiResult.success(syncTaskService.listJobs(jwtService.toAuthenticatedUser(jwt), request));
  }

  @GetMapping("/connectors/{connectorId}/sync-jobs/{jobId}")
  public ApiResult<SyncTaskView> getJob(
    @AuthenticationPrincipal Jwt jwt,
    @PathVariable String connectorId,
    @PathVariable UUID jobId
  ) {
    return ApiResult.success(syncTaskService.getJob(jwtService.toAuthenticatedUser(jwt), connectorId, jobId));
  }
}

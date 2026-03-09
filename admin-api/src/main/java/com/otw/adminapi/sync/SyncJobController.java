package com.otw.adminapi.sync;

import com.otw.adminapi.common.api.ApiResult;
import com.otw.adminapi.security.JwtService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
  public ApiResult<List<SyncTaskView>> listJobs(@AuthenticationPrincipal Jwt jwt) {
    return ApiResult.success(syncTaskService.listJobs(jwtService.toAuthenticatedUser(jwt)));
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

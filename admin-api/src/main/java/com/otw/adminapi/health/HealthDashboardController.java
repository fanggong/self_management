package com.otw.adminapi.health;

import com.otw.adminapi.common.api.ApiResult;
import com.otw.adminapi.security.JwtService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/health-dashboard")
public class HealthDashboardController {
  private final HealthDashboardService healthDashboardService;
  private final JwtService jwtService;

  public HealthDashboardController(HealthDashboardService healthDashboardService, JwtService jwtService) {
    this.healthDashboardService = healthDashboardService;
    this.jwtService = jwtService;
  }

  @GetMapping("/summary")
  public ApiResult<HealthDashboardSummaryView> getSummary(@AuthenticationPrincipal Jwt jwt) {
    return ApiResult.success(healthDashboardService.getSummary(jwtService.toAuthenticatedUser(jwt)));
  }

  @GetMapping("/activities")
  public ApiResult<HealthActivityListResponse> listActivities(
    @AuthenticationPrincipal Jwt jwt,
    @RequestParam(defaultValue = "1") Integer page,
    @RequestParam(defaultValue = "10") Integer pageSize,
    @RequestParam(required = false) String search
  ) {
    return ApiResult.success(healthDashboardService.listActivities(jwtService.toAuthenticatedUser(jwt), page, pageSize, search));
  }

  @GetMapping("/activities/{activityRecordId}")
  public ApiResult<HealthActivityDetailView> getActivityDetail(
    @AuthenticationPrincipal Jwt jwt,
    @PathVariable UUID activityRecordId
  ) {
    return ApiResult.success(healthDashboardService.getActivityDetail(jwtService.toAuthenticatedUser(jwt), activityRecordId));
  }
}

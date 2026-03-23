package com.otw.adminapi.dbt;

import com.otw.adminapi.common.api.ApiResult;
import com.otw.adminapi.security.JwtService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/users/me")
public class DbtModelController {
  private final DbtModelService dbtModelService;
  private final JwtService jwtService;

  public DbtModelController(DbtModelService dbtModelService, JwtService jwtService) {
    this.dbtModelService = dbtModelService;
    this.jwtService = jwtService;
  }

  @GetMapping("/dbt-models")
  public ApiResult<DbtModelListResponse> listModels(
    @AuthenticationPrincipal Jwt jwt,
    @RequestParam String layer,
    @RequestParam(required = false) String search
  ) {
    return ApiResult.success(dbtModelService.listModels(jwtService.toAuthenticatedUser(jwt), layer, search));
  }

  @GetMapping("/dbt-models/{layer}/{modelName}")
  public ApiResult<DbtModelDetailView> getModelDetail(
    @AuthenticationPrincipal Jwt jwt,
    @PathVariable String layer,
    @PathVariable String modelName
  ) {
    return ApiResult.success(dbtModelService.getModelDetail(jwtService.toAuthenticatedUser(jwt), layer, modelName));
  }

  @GetMapping("/dbt-model-runs")
  public ApiResult<DbtRunHistoryListResponse> listRunHistory(
    @AuthenticationPrincipal Jwt jwt,
    @RequestParam(defaultValue = "1") Integer page,
    @RequestParam(defaultValue = "10") Integer pageSize,
    @RequestParam(required = false) String search
  ) {
    return ApiResult.success(
      dbtModelService.listRunHistory(jwtService.toAuthenticatedUser(jwt), new DbtRunHistoryListRequest(page, pageSize, search))
    );
  }

  @GetMapping("/dbt-model-runs/{runId}")
  public ApiResult<DbtRunHistoryDetailView> getRunHistoryDetail(
    @AuthenticationPrincipal Jwt jwt,
    @PathVariable UUID runId
  ) {
    return ApiResult.success(dbtModelService.getRunHistoryDetail(jwtService.toAuthenticatedUser(jwt), runId));
  }

  @GetMapping("/dbt-model-runs/{runId}/models")
  public ApiResult<DbtRunModelHistoryResponse> listRunModels(
    @AuthenticationPrincipal Jwt jwt,
    @PathVariable UUID runId
  ) {
    return ApiResult.success(dbtModelService.listRunModels(jwtService.toAuthenticatedUser(jwt), runId));
  }

  @PostMapping("/dbt-models/run")
  public ResponseEntity<ApiResult<DbtModelRunResultView>> runModel(
    @AuthenticationPrincipal Jwt jwt,
    @Valid @RequestBody RunDbtModelRequest request
  ) {
    DbtModelRunExecution execution = dbtModelService.runModel(jwtService.toAuthenticatedUser(jwt), request);
    return ResponseEntity.status(execution.statusCode())
      .body(new ApiResult<>(execution.success(), execution.data(), execution.message(), execution.code()));
  }

  @PostMapping(value = "/dbt-models/run-stream", produces = "application/x-ndjson")
  public ResponseEntity<StreamingResponseBody> runModelStream(
    @AuthenticationPrincipal Jwt jwt,
    @Valid @RequestBody RunDbtModelRequest request
  ) {
    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType("application/x-ndjson"))
      .body(dbtModelService.streamModelRun(jwtService.toAuthenticatedUser(jwt), request));
  }

  @PostMapping("/dbt-models/run-batch")
  public ApiResult<DbtBatchModelRunResultView> runModelsByScope(
    @AuthenticationPrincipal Jwt jwt,
    @Valid @RequestBody RunDbtModelsByScopeRequest request
  ) {
    return ApiResult.success(dbtModelService.runModelsByScope(jwtService.toAuthenticatedUser(jwt), request));
  }

  @PostMapping(value = "/dbt-models/run-batch-stream", produces = "application/x-ndjson")
  public ResponseEntity<StreamingResponseBody> runModelsByScopeStream(
    @AuthenticationPrincipal Jwt jwt,
    @Valid @RequestBody RunDbtModelsByScopeRequest request
  ) {
    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType("application/x-ndjson"))
      .body(dbtModelService.streamModelsByScope(jwtService.toAuthenticatedUser(jwt), request));
  }
}

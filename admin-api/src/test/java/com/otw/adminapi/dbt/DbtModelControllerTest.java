package com.otw.adminapi.dbt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.otw.adminapi.common.api.ApiResult;
import com.otw.adminapi.security.AuthenticatedUser;
import com.otw.adminapi.security.JwtService;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@ExtendWith(MockitoExtension.class)
class DbtModelControllerTest {
  @Mock
  private DbtModelService dbtModelService;

  @Mock
  private JwtService jwtService;

  private DbtModelController controller;
  private AuthenticatedUser authenticatedUser;
  private Jwt jwt;

  @BeforeEach
  void setUp() {
    controller = new DbtModelController(dbtModelService, jwtService);
    authenticatedUser = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "tester", "USER");
    jwt = Jwt.withTokenValue("token")
      .header("alg", "HS256")
      .subject(authenticatedUser.userId().toString())
      .claim("accountId", authenticatedUser.accountId().toString())
      .claim("principal", authenticatedUser.principal())
      .claim("role", authenticatedUser.role())
      .build();
  }

  @Test
  void listModelsReturnsApiResult() {
    DbtModelListResponse response = new DbtModelListResponse(
      List.of(new DbtModelListItemView("stg_garmin_profile", "staging", "Profile model", "Garmin Connect", null, "garmin-connect", null, "2026-03-20 12:00:00"))
    );
    when(jwtService.toAuthenticatedUser(jwt)).thenReturn(authenticatedUser);
    when(dbtModelService.listModels(authenticatedUser, "staging", "profile")).thenReturn(response);

    ApiResult<DbtModelListResponse> result = controller.listModels(jwt, "staging", "profile");

    assertEquals(true, result.success());
    assertEquals(response, result.data());
    verify(dbtModelService).listModels(authenticatedUser, "staging", "profile");
  }

  @Test
  void getModelDetailReturnsApiResult() {
    DbtModelDetailView response = new DbtModelDetailView(
      "int_health_profile_snapshot",
      "intermediate",
      "Semantic profile model.",
      null,
      "Health",
      "2026-03-20 12:05:00",
      2L,
      List.of(new DbtModelColumnView("account_id", "uuid", "Account identifier."))
    );
    when(jwtService.toAuthenticatedUser(jwt)).thenReturn(authenticatedUser);
    when(dbtModelService.getModelDetail(authenticatedUser, "intermediate", "int_health_profile_snapshot")).thenReturn(response);

    ApiResult<DbtModelDetailView> result = controller.getModelDetail(jwt, "intermediate", "int_health_profile_snapshot");

    assertEquals(true, result.success());
    assertEquals(response, result.data());
  }

  @Test
  void listRunHistoryReturnsApiResult() {
    DbtRunHistoryListResponse response = new DbtRunHistoryListResponse(
      List.of(new DbtRunHistoryListItemView(UUID.randomUUID(), "stg_garmin_profile", "staging", "SUCCESS", 0, 2.0, "2026-03-22 12:00:00", "2026-03-22 12:00:02")),
      new DbtRunHistoryPageView(1, 10, 1, 1)
    );
    when(jwtService.toAuthenticatedUser(jwt)).thenReturn(authenticatedUser);
    when(dbtModelService.listRunHistory(authenticatedUser, new DbtRunHistoryListRequest(1, 10, "success"))).thenReturn(response);

    ApiResult<DbtRunHistoryListResponse> result = controller.listRunHistory(jwt, 1, 10, "success");

    assertEquals(true, result.success());
    assertEquals(response, result.data());
  }

  @Test
  void getRunHistoryDetailReturnsApiResult() {
    UUID runId = UUID.randomUUID();
    DbtRunHistoryDetailView response = new DbtRunHistoryDetailView(
      runId,
      "stg_garmin_profile",
      "staging",
      "SUCCESS",
      0,
      "2026-03-22 12:00:00",
      "2026-03-22 12:00:02",
      null,
      null,
      "stdout",
      ""
    );
    when(jwtService.toAuthenticatedUser(jwt)).thenReturn(authenticatedUser);
    when(dbtModelService.getRunHistoryDetail(authenticatedUser, runId)).thenReturn(response);

    ApiResult<DbtRunHistoryDetailView> result = controller.getRunHistoryDetail(jwt, runId);

    assertEquals(true, result.success());
    assertEquals(response, result.data());
  }

  @Test
  void listRunModelsReturnsApiResult() {
    UUID runId = UUID.randomUUID();
    DbtRunModelHistoryResponse response = new DbtRunModelHistoryResponse(
      List.of(new DbtRunModelHistoryItemView("stg_garmin_profile_snapshot", "staging", "success", "2026-03-22 12:00:01", 0.02))
    );
    when(jwtService.toAuthenticatedUser(jwt)).thenReturn(authenticatedUser);
    when(dbtModelService.listRunModels(authenticatedUser, runId)).thenReturn(response);

    ApiResult<DbtRunModelHistoryResponse> result = controller.listRunModels(jwt, runId);

    assertEquals(true, result.success());
    assertEquals(response, result.data());
  }

  @Test
  void runModelUsesExecutionStatusCode() {
    RunDbtModelRequest request = new RunDbtModelRequest("staging", "stg_garmin_profile");
    DbtModelRunResultView data = new DbtModelRunResultView(false, 1, "stdout", "stderr", "2026-03-20 12:00:00", "2026-03-20 12:00:02");
    DbtModelRunExecution execution = new DbtModelRunExecution(500, false, data, "dbt model run failed.", "DBT_MODEL_RUN_FAILED");
    when(jwtService.toAuthenticatedUser(jwt)).thenReturn(authenticatedUser);
    when(dbtModelService.runModel(authenticatedUser, request)).thenReturn(execution);

    ResponseEntity<ApiResult<DbtModelRunResultView>> response = controller.runModel(jwt, request);

    assertEquals(500, response.getStatusCode().value());
    assertEquals(false, response.getBody().success());
    assertEquals(data, response.getBody().data());
    assertEquals("DBT_MODEL_RUN_FAILED", response.getBody().code());
  }

  @Test
  void runModelStreamReturnsStreamingBody() throws Exception {
    RunDbtModelRequest request = new RunDbtModelRequest("staging", "stg_garmin_profile");
    StreamingResponseBody responseBody = outputStream -> outputStream.write("{\"type\":\"run_started\"}\n".getBytes());
    when(jwtService.toAuthenticatedUser(jwt)).thenReturn(authenticatedUser);
    when(dbtModelService.streamModelRun(authenticatedUser, request)).thenReturn(responseBody);

    ResponseEntity<StreamingResponseBody> response = controller.runModelStream(jwt, request);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    response.getBody().writeTo(outputStream);

    assertEquals(200, response.getStatusCode().value());
    assertEquals("application/x-ndjson", response.getHeaders().getContentType().toString());
    assertEquals("{\"type\":\"run_started\"}\n", outputStream.toString());
  }

  @Test
  void runModelsByScopeReturnsApiResult() {
    RunDbtModelsByScopeRequest request = new RunDbtModelsByScopeRequest("staging", "connector", List.of("garmin-connect"));
    DbtBatchModelRunResultView response = new DbtBatchModelRunResultView(
      "staging",
      "connector",
      List.of("garmin-connect"),
      1,
      1,
      0,
      List.of(new DbtBatchModelRunItemView(
        "stg_garmin_profile_snapshot",
        "staging",
        "garmin-connect",
        "Garmin Connect",
        true,
        0,
        "stdout",
        "",
        "2026-03-20 12:00:00",
        "2026-03-20 12:00:02",
        null,
        "dbt model run completed."
      ))
    );
    when(jwtService.toAuthenticatedUser(jwt)).thenReturn(authenticatedUser);
    when(dbtModelService.runModelsByScope(authenticatedUser, request)).thenReturn(response);

    ApiResult<DbtBatchModelRunResultView> result = controller.runModelsByScope(jwt, request);

    assertEquals(true, result.success());
    assertEquals(response, result.data());
  }

  @Test
  void runModelsByScopeStreamReturnsStreamingBody() throws Exception {
    RunDbtModelsByScopeRequest request = new RunDbtModelsByScopeRequest("staging", "connector", List.of("garmin-connect"));
    StreamingResponseBody responseBody = outputStream -> outputStream.write("{\"type\":\"batch_started\"}\n".getBytes());
    when(jwtService.toAuthenticatedUser(jwt)).thenReturn(authenticatedUser);
    when(dbtModelService.streamModelsByScope(authenticatedUser, request)).thenReturn(responseBody);

    ResponseEntity<StreamingResponseBody> response = controller.runModelsByScopeStream(jwt, request);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    response.getBody().writeTo(outputStream);

    assertEquals(200, response.getStatusCode().value());
    assertEquals("application/x-ndjson", response.getHeaders().getContentType().toString());
    assertEquals("{\"type\":\"batch_started\"}\n", outputStream.toString());
  }
}

package com.otw.adminapi.dbt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.otw.adminapi.common.api.ApiResult;
import com.otw.adminapi.security.AuthenticatedUser;
import com.otw.adminapi.security.JwtService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

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
      List.of(new DbtModelListItemView("stg_garmin_profile", "staging", "2026-03-20 12:00:00"))
    );
    when(jwtService.toAuthenticatedUser(jwt)).thenReturn(authenticatedUser);
    when(dbtModelService.listModels(authenticatedUser, "staging", "profile")).thenReturn(response);

    ApiResult<DbtModelListResponse> result = controller.listModels(jwt, "staging", "profile");

    assertEquals(true, result.success());
    assertEquals(response, result.data());
    verify(dbtModelService).listModels(authenticatedUser, "staging", "profile");
  }

  @Test
  void listRunHistoryReturnsApiResult() {
    DbtRunHistoryListResponse response = new DbtRunHistoryListResponse(
      List.of(new DbtRunHistoryListItemView(UUID.randomUUID(), "stg_garmin_profile", "staging", "SUCCESS", 0, "2026-03-22 12:00:00", "2026-03-22 12:00:02")),
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
}

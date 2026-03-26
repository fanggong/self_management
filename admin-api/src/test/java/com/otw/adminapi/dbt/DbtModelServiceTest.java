package com.otw.adminapi.dbt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otw.adminapi.common.api.ApiException;
import com.otw.adminapi.connector.ConnectorService;
import com.otw.adminapi.security.AuthenticatedUser;
import com.otw.adminapi.user.AccountEntity;
import com.otw.adminapi.user.AccountRepository;
import com.otw.adminapi.user.UserEntity;
import com.otw.adminapi.user.UserRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DbtModelServiceTest {
  @Mock
  private DbtModelRunnerClient dbtModelRunnerClient;

  @Mock
  private DbtRunHistoryWriter dbtRunHistoryWriter;

  @Mock
  private DbtRunHistoryRepository dbtRunHistoryRepository;

  @Mock
  private DbtRunModelHistoryRepository dbtRunModelHistoryRepository;

  @Mock
  private ConnectorService connectorService;

  @Mock
  private AccountRepository accountRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private JdbcTemplate jdbcTemplate;

  private DbtModelService service;
  private AuthenticatedUser authenticatedUser;

  @BeforeEach
  void setUp() {
    service = new DbtModelService(
      dbtModelRunnerClient,
      dbtRunHistoryWriter,
      dbtRunHistoryRepository,
      dbtRunModelHistoryRepository,
      connectorService,
      accountRepository,
      userRepository,
      jdbcTemplate,
      "Asia/Shanghai"
    );
    authenticatedUser = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "tester", "USER");
  }

  @Test
  void listModelsFormatsDatabaseAggregatedTimestamps() {
    when(dbtModelRunnerClient.listModels("staging", "profile")).thenReturn(
      List.of(new DbtModelRunnerClient.RunnerModelItem("stg_garmin_profile", "staging", "Profile model", "garmin-connect", "health", null))
    );
    when(dbtRunModelHistoryRepository.findLatestSuccessfulRuns(authenticatedUser.accountId(), "staging", java.util.Set.of("stg_garmin_profile")))
      .thenReturn(List.of(new DbtModelLatestSuccessfulRunView() {
        @Override
        public String getModelName() {
          return "stg_garmin_profile";
        }

        @Override
        public String getLayer() {
          return "staging";
        }

        @Override
        public Instant getCompletedAt() {
          return Instant.parse("2026-03-20T04:00:00Z");
        }
      }))
    ;
    when(connectorService.getConnectorName("garmin-connect")).thenReturn("Garmin Connect");

    DbtModelListResponse response = service.listModels(authenticatedUser, "staging", "profile");

    assertEquals(1, response.items().size());
    assertEquals("Profile model", response.items().getFirst().description());
    assertEquals("Garmin Connect", response.items().getFirst().connector());
    assertEquals(null, response.items().getFirst().domain());
    assertEquals("garmin-connect", response.items().getFirst().connectorKey());
    assertEquals("health", response.items().getFirst().domainKey());
    assertEquals("2026-03-20 12:00:00", response.items().getFirst().lastRunCompletedAt());
  }

  @Test
  void getModelDetailAggregatesRunnerMetadataAndDatabaseStats() {
    when(dbtModelRunnerClient.getModelDetail("intermediate", "int_health_profile_snapshot")).thenReturn(
      new DbtModelRunnerClient.RunnerModelDetail(
        "int_health_profile_snapshot",
        "intermediate",
        "Semantic profile model.",
        null,
        "health",
        "intermediate",
        "int_health_profile_snapshot",
        List.of(
          new DbtModelRunnerClient.RunnerModelColumn("account_id", "uuid", "Account identifier."),
          new DbtModelRunnerClient.RunnerModelColumn("profile_id", "character varying(255)", "Profile identifier.")
        )
      )
    );
    when(dbtRunModelHistoryRepository.findLatestSuccessfulRuns(authenticatedUser.accountId(), "intermediate", java.util.Set.of("int_health_profile_snapshot")))
      .thenReturn(List.of(new DbtModelLatestSuccessfulRunView() {
        @Override
        public String getModelName() {
          return "int_health_profile_snapshot";
        }

        @Override
        public String getLayer() {
          return "intermediate";
        }

        @Override
        public Instant getCompletedAt() {
          return Instant.parse("2026-03-20T04:05:00Z");
        }
      }));
    when(jdbcTemplate.query(
      org.mockito.ArgumentMatchers.anyString(),
      org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.PreparedStatementSetter.class),
      org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ResultSetExtractor<Long>>any()
    )).thenReturn(2L);

    DbtModelDetailView response = service.getModelDetail(authenticatedUser, "intermediate", "int_health_profile_snapshot");

    assertEquals("int_health_profile_snapshot", response.name());
    assertEquals("Health", response.domain());
    assertEquals(2L, response.estimatedRowCount());
    assertEquals("2026-03-20 12:05:00", response.lastRunCompletedAt());
    assertEquals(2, response.columns().size());
  }

  @Test
  void runModelReturnsFailureExecutionWithStdoutAndStderrAndPersistsHistory() {
    DbtModelRunnerClient.RunnerRunResponse runnerResponse = new DbtModelRunnerClient.RunnerRunResponse(
      500,
      false,
      1,
      "stdout text",
      "stderr text",
      "2026-03-20T04:00:00Z",
      "2026-03-20T04:00:02Z",
      "DBT_MODEL_RUN_FAILED",
      "dbt model run failed.",
      List.of(
        new DbtModelRunnerClient.RunnerExecutedModel(
          "model.otw.stg_garmin_profile",
          "stg_garmin_profile",
          "staging",
          "success",
          null,
          "staging.stg_garmin_profile",
          0.02,
          "2026-03-20T04:00:01Z"
        )
      )
    );
    when(dbtModelRunnerClient.runModel("staging", "stg_garmin_profile")).thenReturn(runnerResponse);

    DbtModelRunExecution execution = service.runModel(authenticatedUser, new RunDbtModelRequest("staging", "stg_garmin_profile"));

    assertEquals(500, execution.statusCode());
    assertEquals(false, execution.success());
    assertEquals("stdout text", execution.data().stdout());
    assertEquals("stderr text", execution.data().stderr());
    assertEquals("2026-03-20 12:00:00", execution.data().startedAt());
    assertEquals("DBT_MODEL_RUN_FAILED", execution.code());
    verify(dbtRunHistoryWriter).recordRunResponse(authenticatedUser, "staging", "stg_garmin_profile", runnerResponse);
  }

  @Test
  void streamModelRunWritesStartedLogAndFinishedEventsWithoutClosingTheStreamEarly() throws Exception {
    DbtModelRunnerClient.RunnerModelRunStream runnerStream = createRunnerModelRunStream("""
{"type":"run_started","layer":"staging","modelName":"stg_garmin_profile_snapshot"}
{"type":"log","stream":"stdout","text":"running upstream model","timestamp":"2026-03-23T08:00:00Z"}
{"type":"run_finished","statusCode":200,"success":true,"returncode":0,"stdout":"full stdout","stderr":"","startedAt":"2026-03-23T08:00:00Z","finishedAt":"2026-03-23T08:00:02Z","message":"dbt model run completed.","executedModels":[]}
""");
    when(dbtModelRunnerClient.openModelRunStream("staging", "stg_garmin_profile_snapshot")).thenReturn(runnerStream);

    var streamBody = service.streamModelRun(
      authenticatedUser,
      new RunDbtModelRequest("staging", "stg_garmin_profile_snapshot")
    );
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    streamBody.writeTo(outputStream);

    String payload = outputStream.toString(StandardCharsets.UTF_8);
    assertEquals(3, payload.lines().count());
    assertTrue(payload.contains("\"type\":\"run_started\""));
    assertTrue(payload.contains("\"type\":\"log\""));
    assertTrue(payload.contains("\"type\":\"run_finished\""));
    verify(dbtRunHistoryWriter).recordRunResponse(
      authenticatedUser,
      "staging",
      "stg_garmin_profile_snapshot",
      new DbtModelRunnerClient.RunnerRunResponse(
        200,
        true,
        0,
        "full stdout",
        "",
        "2026-03-23T08:00:00Z",
        "2026-03-23T08:00:02Z",
        null,
        "dbt model run completed.",
        List.of()
      )
    );
  }

  @Test
  void runModelPersistsBusyRunnerResponsesBeforeThrowingApiException() {
    DbtModelRunnerClient.RunnerRunResponse runnerResponse = new DbtModelRunnerClient.RunnerRunResponse(
      409,
      false,
      null,
      "",
      "",
      null,
      null,
      "DBT_RUNNER_BUSY",
      "Another dbt command is already running.",
      List.of()
    );
    when(dbtModelRunnerClient.runModel("staging", "stg_garmin_profile")).thenReturn(runnerResponse);

    ApiException exception = assertThrows(
      ApiException.class,
      () -> service.runModel(authenticatedUser, new RunDbtModelRequest("staging", "stg_garmin_profile"))
    );

    assertEquals("DBT_RUNNER_BUSY", exception.getCode());
    verify(dbtRunHistoryWriter).recordRunResponse(authenticatedUser, "staging", "stg_garmin_profile", runnerResponse);
  }

  @Test
  void runModelReturnsResponseEvenWhenHistoryPersistenceFails() {
    DbtModelRunnerClient.RunnerRunResponse runnerResponse = new DbtModelRunnerClient.RunnerRunResponse(
      200,
      true,
      0,
      "stdout text",
      "",
      "2026-03-20T04:00:00Z",
      "2026-03-20T04:00:02Z",
      null,
      null,
      List.of()
    );
    when(dbtModelRunnerClient.runModel("staging", "stg_garmin_profile")).thenReturn(runnerResponse);
    doThrow(new IllegalStateException("db down")).when(dbtRunHistoryWriter)
      .recordRunResponse(authenticatedUser, "staging", "stg_garmin_profile", runnerResponse);

    DbtModelRunExecution execution = service.runModel(authenticatedUser, new RunDbtModelRequest("staging", "stg_garmin_profile"));

    assertEquals(200, execution.statusCode());
    assertEquals(true, execution.success());
    assertEquals("stdout text", execution.data().stdout());
    assertEquals("2026-03-20 12:00:02", execution.data().finishedAt());
  }

  @Test
  void runModelsRunsSelectedModelsInNameOrderAndContinuesAfterFailure() {
    when(dbtModelRunnerClient.listModels("staging", null)).thenReturn(
      List.of(
        new DbtModelRunnerClient.RunnerModelItem("stg_medical_report_snapshot", "staging", "Medical report", "medical-report", null, null),
        new DbtModelRunnerClient.RunnerModelItem("stg_garmin_daily_summary", "staging", "Daily summary", "garmin-connect", null, null),
        new DbtModelRunnerClient.RunnerModelItem("stg_garmin_profile_snapshot", "staging", "Profile snapshot", "garmin-connect", null, null)
      )
    );
    when(dbtModelRunnerClient.runModel("staging", "stg_garmin_daily_summary")).thenReturn(
      new DbtModelRunnerClient.RunnerRunResponse(
        500,
        false,
        1,
        "stdout-daily",
        "stderr-daily",
        "2026-03-20T04:00:00Z",
        "2026-03-20T04:00:02Z",
        "DBT_MODEL_RUN_FAILED",
        "dbt model run failed.",
        List.of()
      )
    );
    when(dbtModelRunnerClient.runModel("staging", "stg_garmin_profile_snapshot")).thenReturn(
      new DbtModelRunnerClient.RunnerRunResponse(
        200,
        true,
        0,
        "stdout-profile",
        "",
        "2026-03-20T04:01:00Z",
        "2026-03-20T04:01:02Z",
        null,
        null,
        List.of()
      )
    );
    when(connectorService.getConnectorName("garmin-connect")).thenReturn("Garmin Connect");

    DbtBatchModelRunResultView response = service.runModels(
      authenticatedUser,
      new RunDbtModelsRequest(
        "staging",
        "connector",
        List.of("stg_garmin_profile_snapshot", "stg_garmin_daily_summary")
      )
    );

    assertEquals(2, response.totalModels());
    assertEquals(1, response.succeededCount());
    assertEquals(1, response.failedCount());
    assertEquals(List.of("stg_garmin_daily_summary", "stg_garmin_profile_snapshot"), response.modelNames());
    assertEquals("stg_garmin_daily_summary", response.items().get(0).modelName());
    assertEquals(false, response.items().get(0).success());
    assertEquals("stg_garmin_profile_snapshot", response.items().get(1).modelName());
    assertEquals(true, response.items().get(1).success());
    verify(dbtRunHistoryWriter).recordRunResponse(
      authenticatedUser,
      "staging",
      "stg_garmin_daily_summary",
      new DbtModelRunnerClient.RunnerRunResponse(
        500,
        false,
        1,
        "stdout-daily",
        "stderr-daily",
        "2026-03-20T04:00:00Z",
        "2026-03-20T04:00:02Z",
        "DBT_MODEL_RUN_FAILED",
        "dbt model run failed.",
        List.of()
      )
    );
    verify(dbtRunHistoryWriter).recordRunResponse(
      authenticatedUser,
      "staging",
      "stg_garmin_profile_snapshot",
      new DbtModelRunnerClient.RunnerRunResponse(
        200,
        true,
        0,
        "stdout-profile",
        "",
        "2026-03-20T04:01:00Z",
        "2026-03-20T04:01:02Z",
        null,
        null,
        List.of()
      )
    );
  }

  @Test
  void runDefaultMartsScheduleProcessesAllAccountsFallsBackToEarliestUserAndSkipsAccountsWithoutUsers() {
    UUID accountWithAdminId = UUID.randomUUID();
    UUID accountWithoutAdminId = UUID.randomUUID();
    UUID skippedAccountId = UUID.randomUUID();

    when(accountRepository.findAll()).thenReturn(
      List.of(account(accountWithAdminId), account(accountWithoutAdminId), account(skippedAccountId))
    );
    when(userRepository.findFirstByAccountIdAndRoleOrderByCreatedAtAscIdAsc(accountWithAdminId, "ADMIN"))
      .thenReturn(java.util.Optional.of(user(accountWithAdminId, "admin-user", "ADMIN")));
    when(userRepository.findFirstByAccountIdAndRoleOrderByCreatedAtAscIdAsc(accountWithoutAdminId, "ADMIN"))
      .thenReturn(java.util.Optional.empty());
    when(userRepository.findFirstByAccountIdOrderByCreatedAtAscIdAsc(accountWithoutAdminId))
      .thenReturn(java.util.Optional.of(user(accountWithoutAdminId, "member-user", "USER")));
    when(userRepository.findFirstByAccountIdAndRoleOrderByCreatedAtAscIdAsc(skippedAccountId, "ADMIN"))
      .thenReturn(java.util.Optional.empty());
    when(userRepository.findFirstByAccountIdOrderByCreatedAtAscIdAsc(skippedAccountId))
      .thenReturn(java.util.Optional.empty());
    when(dbtModelRunnerClient.listModels("marts", null)).thenReturn(
      List.of(
        new DbtModelRunnerClient.RunnerModelItem("mart_health_dashboard_daily", "marts", "Daily summary", null, "health", null),
        new DbtModelRunnerClient.RunnerModelItem("mart_health_activity_history", "marts", "Activity history", null, "health", null)
      )
    );
    when(dbtModelRunnerClient.runModel("marts", "mart_health_activity_history")).thenReturn(
      new DbtModelRunnerClient.RunnerRunResponse(200, true, 0, "", "", null, null, null, null, List.of())
    );
    when(dbtModelRunnerClient.runModel("marts", "mart_health_dashboard_daily")).thenReturn(
      new DbtModelRunnerClient.RunnerRunResponse(409, false, null, "", "", null, null, "DBT_RUNNER_BUSY", "busy", List.of())
    );

    DefaultScheduledMartsRunResultView result = service.runDefaultMartsSchedule();

    assertEquals(2, result.processedAccounts());
    assertEquals(1, result.skippedAccounts());
    assertEquals(2, result.succeededModels());
    assertEquals(2, result.failedModels());
    verify(dbtRunHistoryWriter).recordRunResponse(
      org.mockito.ArgumentMatchers.argThat(user -> user.equals(
        new AuthenticatedUser(user(accountWithAdminId, "admin-user", "ADMIN").getId(), accountWithAdminId, "admin-user", "ADMIN")
      )),
      org.mockito.ArgumentMatchers.eq("marts"),
      org.mockito.ArgumentMatchers.eq("mart_health_activity_history"),
      org.mockito.ArgumentMatchers.any()
    );
    verify(dbtRunHistoryWriter).recordRunResponse(
      org.mockito.ArgumentMatchers.argThat(user -> user.equals(
        new AuthenticatedUser(user(accountWithoutAdminId, "member-user", "USER").getId(), accountWithoutAdminId, "member-user", "USER")
      )),
      org.mockito.ArgumentMatchers.eq("marts"),
      org.mockito.ArgumentMatchers.eq("mart_health_activity_history"),
      org.mockito.ArgumentMatchers.any()
    );
  }

  private AccountEntity account(UUID id) {
    AccountEntity entity = new AccountEntity();
    ReflectionTestUtils.setField(entity, "id", id);
    ReflectionTestUtils.setField(entity, "name", "account-" + id);
    return entity;
  }

  private UserEntity user(UUID accountId, String principal, String role) {
    UserEntity entity = new UserEntity();
    ReflectionTestUtils.setField(entity, "id", UUID.nameUUIDFromBytes((accountId + ":" + principal).getBytes(StandardCharsets.UTF_8)));
    entity.setAccountId(accountId);
    entity.setPrincipal(principal);
    entity.setDisplayName(principal);
    entity.setRole(role);
    entity.setPasswordHash("secret");
    return entity;
  }

  @Test
  void listRunHistoryReturnsPaginatedRunsSortedByRepositoryQuery() {
    UUID runId = UUID.randomUUID();
    DbtRunHistoryEntity entity = new DbtRunHistoryEntity();
    ReflectionTestUtils.setField(entity, "id", runId);
    entity.setRequestedModelName("int_health_profile_snapshot");
    entity.setRequestedLayer("intermediate");
    entity.setStatus("FAILED");
    entity.setReturncode(1);
    entity.setStartedAt(Instant.parse("2026-03-22T02:10:00Z"));
    entity.setFinishedAt(Instant.parse("2026-03-22T02:10:09Z"));

    when(dbtRunHistoryRepository.findPageByAccountIdAndSearch(
      authenticatedUser.accountId(),
      "failed",
      PageRequest.of(0, 10)
    )).thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 10), 1));

    DbtRunHistoryListResponse response = service.listRunHistory(authenticatedUser, new DbtRunHistoryListRequest(1, 10, "FAILED"));

    assertEquals(1, response.items().size());
    assertEquals(runId, response.items().getFirst().runId());
    assertEquals(9.0, response.items().getFirst().executionTimeSeconds());
    assertEquals("2026-03-22 10:10:00", response.items().getFirst().startedAt());
    assertEquals(1, response.page().total());
    assertEquals(1, response.page().totalPages());
  }

  @Test
  void listRunHistoryWithoutSearchUsesAccountScopedQuery() {
    when(dbtRunHistoryRepository.findPageByAccountId(authenticatedUser.accountId(), PageRequest.of(0, 10)))
      .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

    DbtRunHistoryListResponse response = service.listRunHistory(authenticatedUser, new DbtRunHistoryListRequest(1, 10, null));

    assertEquals(0, response.items().size());
    assertEquals(0, response.page().total());
  }

  @Test
  void getRunHistoryDetailReturnsFormattedLogPayload() {
    UUID runId = UUID.randomUUID();
    DbtRunHistoryEntity entity = new DbtRunHistoryEntity();
    ReflectionTestUtils.setField(entity, "id", runId);
    entity.setRequestedModelName("stg_garmin_profile_snapshot");
    entity.setRequestedLayer("staging");
    entity.setStatus("SUCCESS");
    entity.setReturncode(0);
    entity.setStartedAt(Instant.parse("2026-03-22T03:00:00Z"));
    entity.setFinishedAt(Instant.parse("2026-03-22T03:00:05Z"));
    entity.setStdout("dbt run output");
    entity.setStderr("");

    when(dbtRunHistoryRepository.findByIdAndAccountId(runId, authenticatedUser.accountId())).thenReturn(java.util.Optional.of(entity));

    DbtRunHistoryDetailView response = service.getRunHistoryDetail(authenticatedUser, runId);

    assertEquals(runId, response.runId());
    assertEquals("2026-03-22 11:00:00", response.startedAt());
    assertEquals("dbt run output", response.stdout());
  }

  @Test
  void listRunModelsReturnsExecutionOrderedChildRows() {
    UUID runId = UUID.randomUUID();
    DbtRunHistoryEntity parent = new DbtRunHistoryEntity();
    ReflectionTestUtils.setField(parent, "id", runId);

    DbtRunModelHistoryEntity first = new DbtRunModelHistoryEntity();
    first.setDbtRunHistoryId(runId);
    first.setAccountId(authenticatedUser.accountId());
    first.setModelName("stg_garmin_profile_snapshot");
    first.setLayer("staging");
    first.setStatus("success");
    first.setCompletedAt(Instant.parse("2026-03-22T03:00:01Z"));
    first.setExecutionTimeSeconds(0.02);

    DbtRunModelHistoryEntity second = new DbtRunModelHistoryEntity();
    second.setDbtRunHistoryId(runId);
    second.setAccountId(authenticatedUser.accountId());
    second.setModelName("int_health_profile_snapshot");
    second.setLayer("intermediate");
    second.setStatus("error");
    second.setCompletedAt(Instant.parse("2026-03-22T03:00:03Z"));
    second.setExecutionTimeSeconds(0.01);

    when(dbtRunHistoryRepository.findByIdAndAccountId(runId, authenticatedUser.accountId())).thenReturn(java.util.Optional.of(parent));
    when(dbtRunModelHistoryRepository.findByRunIdAndAccountIdOrderByExecution(runId, authenticatedUser.accountId()))
      .thenReturn(List.of(first, second));

    DbtRunModelHistoryResponse response = service.listRunModels(authenticatedUser, runId);

    assertEquals(2, response.items().size());
    assertEquals("stg_garmin_profile_snapshot", response.items().get(0).modelName());
    assertEquals("2026-03-22 11:00:01", response.items().get(0).completedAt());
    assertEquals("int_health_profile_snapshot", response.items().get(1).modelName());
  }

  @Test
  void listRunModelsRejectsMissingParentRun() {
    UUID runId = UUID.randomUUID();
    when(dbtRunHistoryRepository.findByIdAndAccountId(runId, authenticatedUser.accountId())).thenReturn(java.util.Optional.empty());

    ApiException exception = assertThrows(ApiException.class, () -> service.listRunModels(authenticatedUser, runId));

    assertEquals("DBT_RUN_HISTORY_NOT_FOUND", exception.getCode());
  }

  @Test
  void listRunHistoryRejectsInvalidPagination() {
    ApiException exception = assertThrows(
      ApiException.class,
      () -> service.listRunHistory(authenticatedUser, new DbtRunHistoryListRequest(0, 10, null))
    );

    assertEquals("VALIDATION_ERROR", exception.getCode());
  }

  @Test
  void listModelsRejectsInvalidLayer() {
    ApiException exception = assertThrows(ApiException.class, () -> service.listModels(authenticatedUser, "logs", null));

    assertEquals("VALIDATION_ERROR", exception.getCode());
  }

  private DbtModelRunnerClient.RunnerModelRunStream createRunnerModelRunStream(String payload) throws Exception {
    Constructor<DbtModelRunnerClient.RunnerModelRunStream> constructor = DbtModelRunnerClient.RunnerModelRunStream.class
      .getDeclaredConstructor(java.io.InputStream.class, ObjectMapper.class);
    constructor.setAccessible(true);
    return constructor.newInstance(
      new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)),
      new ObjectMapper()
    );
  }
}

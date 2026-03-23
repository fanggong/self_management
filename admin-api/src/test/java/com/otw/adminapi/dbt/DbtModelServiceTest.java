package com.otw.adminapi.dbt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.otw.adminapi.common.api.ApiException;
import com.otw.adminapi.security.AuthenticatedUser;
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

  private DbtModelService service;
  private AuthenticatedUser authenticatedUser;

  @BeforeEach
  void setUp() {
    service = new DbtModelService(
      dbtModelRunnerClient,
      dbtRunHistoryWriter,
      dbtRunHistoryRepository,
      dbtRunModelHistoryRepository,
      "Asia/Shanghai"
    );
    authenticatedUser = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "tester", "USER");
  }

  @Test
  void listModelsFormatsDatabaseAggregatedTimestamps() {
    when(dbtModelRunnerClient.listModels("staging", "profile")).thenReturn(
      List.of(new DbtModelRunnerClient.RunnerModelItem("stg_garmin_profile", "staging", null))
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

    DbtModelListResponse response = service.listModels(authenticatedUser, "staging", "profile");

    assertEquals(1, response.items().size());
    assertEquals("2026-03-20 12:00:00", response.items().getFirst().lastRunCompletedAt());
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
}

package com.otw.adminapi.dbt;

import com.otw.adminapi.security.AuthenticatedUser;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DbtRunHistoryWriter {
  private final DbtRunHistoryRepository dbtRunHistoryRepository;
  private final DbtRunModelHistoryRepository dbtRunModelHistoryRepository;

  public DbtRunHistoryWriter(
    DbtRunHistoryRepository dbtRunHistoryRepository,
    DbtRunModelHistoryRepository dbtRunModelHistoryRepository
  ) {
    this.dbtRunHistoryRepository = dbtRunHistoryRepository;
    this.dbtRunModelHistoryRepository = dbtRunModelHistoryRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordRunResponse(
    AuthenticatedUser authenticatedUser,
    String requestedLayer,
    String requestedModelName,
    DbtModelRunnerClient.RunnerRunResponse response
  ) {
    DbtRunHistoryEntity runHistory = new DbtRunHistoryEntity();
    runHistory.setAccountId(authenticatedUser.accountId());
    runHistory.setUserId(authenticatedUser.userId());
    runHistory.setRequestedLayer(requestedLayer);
    runHistory.setRequestedModelName(requestedModelName);
    runHistory.setSelector("+" + requestedModelName);
    runHistory.setStatus(resolveRunStatus(response.statusCode(), response.success()));
    runHistory.setReturncode(response.returncode());
    runHistory.setStdout(normalize(response.stdout()));
    runHistory.setStderr(normalize(response.stderr()));
    runHistory.setErrorCode(resolveErrorCode(response));
    runHistory.setErrorMessage(resolveErrorMessage(response));
    runHistory.setStartedAt(parseInstantOrNull(response.startedAt()));
    runHistory.setFinishedAt(parseInstantOrNull(response.finishedAt()));

    DbtRunHistoryEntity persistedRunHistory = dbtRunHistoryRepository.save(runHistory);
    if (response.executedModels().isEmpty()) {
      return;
    }

    List<DbtRunModelHistoryEntity> modelHistories = new ArrayList<>();
    for (DbtModelRunnerClient.RunnerExecutedModel executedModel : response.executedModels()) {
      String layer = normalizeNullable(executedModel.layer());
      String modelName = normalizeNullable(executedModel.name());
      String uniqueId = normalizeNullable(executedModel.uniqueId());
      if (layer == null || modelName == null || uniqueId == null) {
        continue;
      }

      DbtRunModelHistoryEntity modelHistory = new DbtRunModelHistoryEntity();
      modelHistory.setDbtRunHistoryId(persistedRunHistory.getId());
      modelHistory.setAccountId(authenticatedUser.accountId());
      modelHistory.setLayer(layer);
      modelHistory.setModelName(modelName);
      modelHistory.setUniqueId(uniqueId);
      modelHistory.setStatus(normalizeStatus(executedModel.status()));
      modelHistory.setMessage(normalizeNullable(executedModel.message()));
      modelHistory.setRelationName(normalizeNullable(executedModel.relationName()));
      modelHistory.setExecutionTimeSeconds(executedModel.executionTimeSeconds());
      modelHistory.setCompletedAt(parseInstantOrNull(executedModel.completedAt()));
      modelHistories.add(modelHistory);
    }
    if (!modelHistories.isEmpty()) {
      dbtRunModelHistoryRepository.saveAll(modelHistories);
    }
  }

  private String resolveRunStatus(int statusCode, boolean success) {
    if (statusCode == 200 && success) {
      return "SUCCESS";
    }
    if (statusCode == 500) {
      return "FAILED";
    }
    if (statusCode == 409) {
      return "BUSY";
    }
    return "RUNNER_ERROR";
  }

  private String resolveErrorCode(DbtModelRunnerClient.RunnerRunResponse response) {
    if (response.statusCode() == 200 && response.success()) {
      return null;
    }
    return normalizeNullable(response.code()) != null ? normalizeNullable(response.code()) : "DBT_MODEL_RUN_FAILED";
  }

  private String resolveErrorMessage(DbtModelRunnerClient.RunnerRunResponse response) {
    if (response.statusCode() == 200 && response.success()) {
      return null;
    }
    return normalizeNullable(response.message()) != null ? normalizeNullable(response.message()) : "dbt model run failed.";
  }

  private String normalizeStatus(String value) {
    String normalized = normalizeNullable(value);
    return normalized == null ? "unknown" : normalized.toLowerCase(Locale.ROOT);
  }

  private String normalizeNullable(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private Instant parseInstantOrNull(String value) {
    String normalized = normalizeNullable(value);
    if (normalized == null) {
      return null;
    }

    try {
      return Instant.parse(normalized);
    } catch (DateTimeParseException exception) {
      return null;
    }
  }

  private String normalize(String value) {
    return value == null ? "" : value;
  }
}

package com.otw.adminapi.dbt;

import com.otw.adminapi.common.api.ApiException;
import com.otw.adminapi.common.util.DateTimeFormats;
import com.otw.adminapi.security.AuthenticatedUser;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DbtModelService {
  private static final Set<String> VALID_LAYERS = Set.of("staging", "intermediate", "marts");
  private static final int DEFAULT_RUN_HISTORY_PAGE = 1;
  private static final int DEFAULT_RUN_HISTORY_PAGE_SIZE = 10;
  private static final int MAX_RUN_HISTORY_PAGE_SIZE = 100;
  private static final Logger log = LoggerFactory.getLogger(DbtModelService.class);

  private final DbtModelRunnerClient dbtModelRunnerClient;
  private final DbtRunHistoryWriter dbtRunHistoryWriter;
  private final DbtRunHistoryRepository dbtRunHistoryRepository;
  private final DbtRunModelHistoryRepository dbtRunModelHistoryRepository;
  private final ZoneId zoneId;

  public DbtModelService(
    DbtModelRunnerClient dbtModelRunnerClient,
    DbtRunHistoryWriter dbtRunHistoryWriter,
    DbtRunHistoryRepository dbtRunHistoryRepository,
    DbtRunModelHistoryRepository dbtRunModelHistoryRepository,
    @Value("${app.timezone}") String timezone
  ) {
    this.dbtModelRunnerClient = dbtModelRunnerClient;
    this.dbtRunHistoryWriter = dbtRunHistoryWriter;
    this.dbtRunHistoryRepository = dbtRunHistoryRepository;
    this.dbtRunModelHistoryRepository = dbtRunModelHistoryRepository;
    this.zoneId = ZoneId.of(timezone);
  }

  public DbtModelListResponse listModels(AuthenticatedUser authenticatedUser, String layer, String search) {
    String normalizedLayer = normalizeLayer(layer);
    var runnerItems = dbtModelRunnerClient.listModels(normalizedLayer, normalizeNullable(search));
    Map<String, Instant> latestRuns = loadLatestSuccessfulRuns(authenticatedUser, normalizedLayer, runnerItems);

    return new DbtModelListResponse(
      runnerItems
        .stream()
        .map(item -> new DbtModelListItemView(
          item.name(),
          item.layer(),
          DateTimeFormats.formatNullableOrNull(latestRuns.get(item.name()), zoneId)
        ))
        .toList()
    );
  }

  public DbtRunHistoryListResponse listRunHistory(AuthenticatedUser authenticatedUser, DbtRunHistoryListRequest request) {
    RunHistoryQueryOptions queryOptions = parseRunHistoryQueryOptions(request);
    var pageable = PageRequest.of(queryOptions.page() - 1, queryOptions.pageSize());
    var page = queryOptions.search() == null
      ? dbtRunHistoryRepository.findPageByAccountId(authenticatedUser.accountId(), pageable)
      : dbtRunHistoryRepository.findPageByAccountIdAndSearch(authenticatedUser.accountId(), queryOptions.search(), pageable);

    return new DbtRunHistoryListResponse(
      page.getContent().stream()
        .map(this::toRunHistoryListItemView)
        .toList(),
      new DbtRunHistoryPageView(
        queryOptions.page(),
        queryOptions.pageSize(),
        (int) page.getTotalElements(),
        page.getTotalPages()
      )
    );
  }

  public DbtRunHistoryDetailView getRunHistoryDetail(AuthenticatedUser authenticatedUser, UUID runId) {
    DbtRunHistoryEntity entity = dbtRunHistoryRepository.findByIdAndAccountId(runId, authenticatedUser.accountId())
      .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DBT_RUN_HISTORY_NOT_FOUND", "dbt run history was not found."));

    return toRunHistoryDetailView(entity);
  }

  @Transactional
  public DbtModelRunExecution runModel(AuthenticatedUser authenticatedUser, RunDbtModelRequest request) {
    String normalizedLayer = normalizeLayer(request.layer());
    String modelName = normalizeRequired(request.modelName(), "Model name is required.");

    DbtModelRunnerClient.RunnerRunResponse response;
    try {
      response = dbtModelRunnerClient.runModel(normalizedLayer, modelName);
    } catch (ApiException exception) {
      if (!"DBT_MODEL_RUN_REQUEST_ERROR".equals(exception.getCode())) {
        persistRunResponseSafely(
          authenticatedUser,
          normalizedLayer,
          modelName,
          new DbtModelRunnerClient.RunnerRunResponse(
            exception.getStatus().value(),
            false,
            null,
            "",
            "",
            null,
            null,
            exception.getCode(),
            exception.getMessage(),
            java.util.List.of()
          )
        );
      }
      throw exception;
    }

    persistRunResponseSafely(authenticatedUser, normalizedLayer, modelName, response);
    DbtModelRunResultView data = new DbtModelRunResultView(
      response.success(),
      response.returncode() == null ? 0 : response.returncode(),
      response.stdout(),
      response.stderr(),
      formatInstantOrNull(response.startedAt()),
      formatInstantOrNull(response.finishedAt())
    );

    if (response.statusCode() == 200) {
      return new DbtModelRunExecution(200, true, data, "dbt model run completed.", null);
    }

    if (response.statusCode() == 500) {
      return new DbtModelRunExecution(
        500,
        false,
        data,
        response.message() != null ? response.message() : "dbt model run failed.",
        response.code() != null ? response.code() : "DBT_MODEL_RUN_FAILED"
      );
    }

    throw new ApiException(
      HttpStatus.valueOf(response.statusCode()),
      response.code() != null ? response.code() : "DBT_MODEL_RUN_FAILED",
      response.message() != null ? response.message() : "dbt model run failed."
    );
  }

  private Map<String, Instant> loadLatestSuccessfulRuns(
    AuthenticatedUser authenticatedUser,
    String normalizedLayer,
    java.util.List<DbtModelRunnerClient.RunnerModelItem> runnerItems
  ) {
    Set<String> modelNames = runnerItems.stream()
      .map(DbtModelRunnerClient.RunnerModelItem::name)
      .filter(name -> name != null && !name.isBlank())
      .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

    if (modelNames.isEmpty()) {
      return Map.of();
    }

    return dbtRunModelHistoryRepository.findLatestSuccessfulRuns(authenticatedUser.accountId(), normalizedLayer, modelNames)
      .stream()
      .filter(item -> item.getModelName() != null && item.getCompletedAt() != null)
      .collect(java.util.stream.Collectors.toMap(
        DbtModelLatestSuccessfulRunView::getModelName,
        DbtModelLatestSuccessfulRunView::getCompletedAt,
        (left, right) -> right.isAfter(left) ? right : left
      ));
  }

  private DbtRunHistoryListItemView toRunHistoryListItemView(DbtRunHistoryEntity entity) {
    return new DbtRunHistoryListItemView(
      entity.getId(),
      entity.getRequestedModelName(),
      entity.getRequestedLayer(),
      entity.getStatus(),
      entity.getReturncode(),
      DateTimeFormats.formatNullableOrNull(entity.getStartedAt(), zoneId),
      DateTimeFormats.formatNullableOrNull(entity.getFinishedAt(), zoneId)
    );
  }

  private DbtRunHistoryDetailView toRunHistoryDetailView(DbtRunHistoryEntity entity) {
    return new DbtRunHistoryDetailView(
      entity.getId(),
      entity.getRequestedModelName(),
      entity.getRequestedLayer(),
      entity.getStatus(),
      entity.getReturncode(),
      DateTimeFormats.formatNullableOrNull(entity.getStartedAt(), zoneId),
      DateTimeFormats.formatNullableOrNull(entity.getFinishedAt(), zoneId),
      entity.getErrorCode(),
      entity.getErrorMessage(),
      entity.getStdout(),
      entity.getStderr()
    );
  }

  private void persistRunResponseSafely(
    AuthenticatedUser authenticatedUser,
    String normalizedLayer,
    String modelName,
    DbtModelRunnerClient.RunnerRunResponse response
  ) {
    try {
      dbtRunHistoryWriter.recordRunResponse(authenticatedUser, normalizedLayer, modelName, response);
    } catch (Exception exception) {
      log.warn(
        "Failed to persist dbt run history. layer={}, modelName={}, statusCode={}",
        normalizedLayer,
        modelName,
        response.statusCode(),
        exception
      );
    }
  }

  private String normalizeLayer(String value) {
    String normalized = normalizeRequired(value, "Layer is required.").toLowerCase();
    if (!VALID_LAYERS.contains(normalized)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Layer must be staging, intermediate, or marts.");
    }
    return normalized;
  }

  private String normalizeRequired(String value, String message) {
    if (value == null || value.trim().isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
    return value.trim();
  }

  private String normalizeNullable(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private String formatInstantOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    return DateTimeFormats.formatNullableOrNull(Instant.parse(value), zoneId);
  }

  private RunHistoryQueryOptions parseRunHistoryQueryOptions(DbtRunHistoryListRequest request) {
    int page = request.page() == null ? DEFAULT_RUN_HISTORY_PAGE : request.page();
    int pageSize = request.pageSize() == null ? DEFAULT_RUN_HISTORY_PAGE_SIZE : request.pageSize();
    if (page < 1 || pageSize <= 0 || pageSize > MAX_RUN_HISTORY_PAGE_SIZE) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid pagination parameters.");
    }

    return new RunHistoryQueryOptions(page, pageSize, normalizeNullable(request.search()) == null ? null : normalizeNullable(request.search()).toLowerCase());
  }

  private record RunHistoryQueryOptions(
    int page,
    int pageSize,
    String search
  ) {
  }
}

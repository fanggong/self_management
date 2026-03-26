package com.otw.adminapi.dbt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otw.adminapi.common.api.ApiException;
import com.otw.adminapi.common.util.DateTimeFormats;
import com.otw.adminapi.connector.ConnectorService;
import com.otw.adminapi.security.AuthenticatedUser;
import com.otw.adminapi.user.AccountRepository;
import com.otw.adminapi.user.UserEntity;
import com.otw.adminapi.user.UserRepository;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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
  private final ConnectorService connectorService;
  private final AccountRepository accountRepository;
  private final UserRepository userRepository;
  private final JdbcTemplate jdbcTemplate;
  private final ZoneId zoneId;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public DbtModelService(
    DbtModelRunnerClient dbtModelRunnerClient,
    DbtRunHistoryWriter dbtRunHistoryWriter,
    DbtRunHistoryRepository dbtRunHistoryRepository,
    DbtRunModelHistoryRepository dbtRunModelHistoryRepository,
    ConnectorService connectorService,
    AccountRepository accountRepository,
    UserRepository userRepository,
    JdbcTemplate jdbcTemplate,
    @Value("${app.timezone}") String timezone
  ) {
    this.dbtModelRunnerClient = dbtModelRunnerClient;
    this.dbtRunHistoryWriter = dbtRunHistoryWriter;
    this.dbtRunHistoryRepository = dbtRunHistoryRepository;
    this.dbtRunModelHistoryRepository = dbtRunModelHistoryRepository;
    this.connectorService = connectorService;
    this.accountRepository = accountRepository;
    this.userRepository = userRepository;
    this.jdbcTemplate = jdbcTemplate;
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
          item.description(),
          toConnectorDisplay(item.layer(), item.connectorId()),
          toDomainDisplay(item.layer(), item.domainKey()),
          normalizeNullable(item.connectorId()),
          normalizeNullable(item.domainKey()),
          DateTimeFormats.formatNullableOrNull(latestRuns.get(item.name()), zoneId)
        ))
        .toList()
    );
  }

  public DbtModelDetailView getModelDetail(AuthenticatedUser authenticatedUser, String layer, String modelName) {
    String normalizedLayer = normalizeLayer(layer);
    String normalizedModelName = normalizeRequired(modelName, "Model name is required.");
    DbtModelRunnerClient.RunnerModelDetail detail = dbtModelRunnerClient.getModelDetail(normalizedLayer, normalizedModelName);

    return new DbtModelDetailView(
      detail.name(),
      detail.layer(),
      detail.description(),
      toConnectorDisplay(normalizedLayer, detail.connectorId()),
      toDomainDisplayForDetail(detail.domainKey()),
      loadLatestSuccessfulRunAt(authenticatedUser, normalizedLayer, normalizedModelName),
      loadEstimatedRowCount(detail.schemaName(), detail.relationName()),
      detail.columns().stream()
        .map(column -> new DbtModelColumnView(column.name(), column.type(), column.description()))
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
    DbtRunHistoryEntity entity = requireRunHistory(authenticatedUser, runId);

    return toRunHistoryDetailView(entity);
  }

  public DbtRunModelHistoryResponse listRunModels(AuthenticatedUser authenticatedUser, UUID runId) {
    requireRunHistory(authenticatedUser, runId);

    return new DbtRunModelHistoryResponse(
      dbtRunModelHistoryRepository.findByRunIdAndAccountIdOrderByExecution(runId, authenticatedUser.accountId())
        .stream()
        .map(this::toRunModelHistoryItemView)
        .toList()
    );
  }

  @Transactional
  public DbtModelRunExecution runModel(AuthenticatedUser authenticatedUser, RunDbtModelRequest request) {
    String normalizedLayer = normalizeLayer(request.layer());
    String modelName = normalizeRequired(request.modelName(), "Model name is required.");
    SingleModelRunOutcome outcome = executeSingleModel(authenticatedUser, normalizedLayer, modelName);
    DbtModelRunResultView data = new DbtModelRunResultView(
      outcome.success(),
      outcome.returncode() == null ? 0 : outcome.returncode(),
      outcome.stdout(),
      outcome.stderr(),
      outcome.startedAt(),
      outcome.finishedAt()
    );

    if (outcome.statusCode() == 200) {
      return new DbtModelRunExecution(200, true, data, outcome.message(), outcome.code());
    }

    if (outcome.statusCode() == 500) {
      return new DbtModelRunExecution(
        500,
        false,
        data,
        outcome.message(),
        outcome.code()
      );
    }

    throw new ApiException(
      HttpStatus.resolve(outcome.statusCode()) == null ? HttpStatus.BAD_GATEWAY : HttpStatus.valueOf(outcome.statusCode()),
      outcome.code(),
      outcome.message()
    );
  }

  @Transactional
  public StreamingResponseBody streamModelRun(AuthenticatedUser authenticatedUser, RunDbtModelRequest request) {
    String normalizedLayer = normalizeLayer(request.layer());
    String modelName = normalizeRequired(request.modelName(), "Model name is required.");
    DbtModelRunnerClient.RunnerModelRunStream runStream = openModelRunStreamWithPersistence(
      authenticatedUser,
      normalizedLayer,
      modelName
    );

    return outputStream -> {
      AtomicBoolean outputWritable = new AtomicBoolean(true);
      writeStreamEventIfPossible(outputStream, outputWritable, createRunStartedEvent(normalizedLayer, modelName));

      DbtModelRunnerClient.RunnerRunResponse response;
      try (runStream) {
        response = runStream.consume(logEvent -> writeStreamEventIfPossible(
          outputStream,
          outputWritable,
          createLogEvent(logEvent, null)
        ));
      } catch (Exception exception) {
        DbtModelRunnerClient.RunnerRunResponse failureResponse = createUnexpectedStreamFailureResponse(exception);
        persistRunResponseSafely(authenticatedUser, normalizedLayer, modelName, failureResponse);
        SingleModelRunOutcome failureOutcome = toSingleModelRunOutcome(failureResponse);
        writeStreamEventIfPossible(
          outputStream,
          outputWritable,
          createRunFinishedEvent(normalizedLayer, modelName, failureOutcome)
        );
        return;
      }

      persistRunResponseSafely(authenticatedUser, normalizedLayer, modelName, response);
      SingleModelRunOutcome outcome = toSingleModelRunOutcome(response);
      writeStreamEventIfPossible(outputStream, outputWritable, createRunFinishedEvent(normalizedLayer, modelName, outcome));
    };
  }

  @Transactional
  public DbtBatchModelRunResultView runModels(AuthenticatedUser authenticatedUser, RunDbtModelsRequest request) {
    String normalizedLayer = normalizeLayer(request.layer());
    String normalizedSelectionType = normalizeSelectionType(normalizedLayer, request.selectionType());
    List<DbtModelRunnerClient.RunnerModelItem> targetModels = resolveSelectedModels(normalizedLayer, normalizedSelectionType, request.modelNames());
    List<String> modelNames = targetModels.stream().map(DbtModelRunnerClient.RunnerModelItem::name).toList();

    List<DbtBatchModelRunItemView> items = new ArrayList<>();
    for (DbtModelRunnerClient.RunnerModelItem item : targetModels) {
      SingleModelRunOutcome outcome = executeSingleModel(authenticatedUser, normalizedLayer, item.name());
      items.add(new DbtBatchModelRunItemView(
        item.name(),
        item.layer(),
        resolveScopeKey(normalizedSelectionType, item),
        resolveScopeLabel(normalizedLayer, normalizedSelectionType, item),
        outcome.success(),
        outcome.returncode(),
        outcome.stdout(),
        outcome.stderr(),
        outcome.startedAt(),
        outcome.finishedAt(),
        outcome.code(),
        outcome.message()
      ));
    }

    int succeededCount = (int) items.stream().filter(DbtBatchModelRunItemView::success).count();
    return new DbtBatchModelRunResultView(
      normalizedLayer,
      normalizedSelectionType,
      modelNames,
      items.size(),
      succeededCount,
      items.size() - succeededCount,
      items
    );
  }

  @Transactional
  public StreamingResponseBody streamModels(AuthenticatedUser authenticatedUser, RunDbtModelsRequest request) {
    String normalizedLayer = normalizeLayer(request.layer());
    String normalizedSelectionType = normalizeSelectionType(normalizedLayer, request.selectionType());
    List<DbtModelRunnerClient.RunnerModelItem> targetModels = resolveSelectedModels(normalizedLayer, normalizedSelectionType, request.modelNames());
    List<String> modelNames = targetModels.stream().map(DbtModelRunnerClient.RunnerModelItem::name).toList();
    DbtModelRunnerClient.RunnerModelRunStream firstStream = targetModels.isEmpty()
      ? null
      : openModelRunStreamWithPersistence(authenticatedUser, normalizedLayer, targetModels.getFirst().name());

    return outputStream -> {
      AtomicBoolean outputWritable = new AtomicBoolean(true);
      List<DbtBatchModelRunItemView> finishedItems = new ArrayList<>();

      writeStreamEventIfPossible(
        outputStream,
        outputWritable,
        createBatchStartedEvent(normalizedLayer, normalizedSelectionType, modelNames, targetModels)
      );

      int succeededCount = 0;
      int failedCount = 0;
      for (int index = 0; index < targetModels.size(); index++) {
        DbtModelRunnerClient.RunnerModelItem targetModel = targetModels.get(index);
        String scopeKey = resolveScopeKey(normalizedSelectionType, targetModel);
        String scopeLabel = resolveScopeLabel(normalizedLayer, normalizedSelectionType, targetModel);

        writeStreamEventIfPossible(
          outputStream,
          outputWritable,
          createTargetStartedEvent(normalizedLayer, targetModel.name(), scopeKey, scopeLabel)
        );

        DbtModelRunnerClient.RunnerRunResponse response;
        boolean responsePersisted = false;
        try (DbtModelRunnerClient.RunnerModelRunStream runStream = index == 0
          ? firstStream
          : openModelRunStreamWithPersistence(authenticatedUser, normalizedLayer, targetModel.name())) {
          response = runStream.consume(logEvent -> writeStreamEventIfPossible(
            outputStream,
            outputWritable,
            createLogEvent(logEvent, targetModel.name())
          ));
        } catch (ApiException exception) {
          response = createFailedRunnerResponse(exception);
          responsePersisted = true;
        } catch (Exception exception) {
          response = createUnexpectedStreamFailureResponse(exception);
          persistRunResponseSafely(authenticatedUser, normalizedLayer, targetModel.name(), response);
          responsePersisted = true;
        }

        if (!responsePersisted) {
          persistRunResponseSafely(authenticatedUser, normalizedLayer, targetModel.name(), response);
        }

        SingleModelRunOutcome outcome = toSingleModelRunOutcome(response);
        DbtBatchModelRunItemView itemView = new DbtBatchModelRunItemView(
          targetModel.name(),
          targetModel.layer(),
          scopeKey,
          scopeLabel,
          outcome.success(),
          outcome.returncode(),
          outcome.stdout(),
          outcome.stderr(),
          outcome.startedAt(),
          outcome.finishedAt(),
          outcome.code(),
          outcome.message()
        );
        finishedItems.add(itemView);
        if (itemView.success()) {
          succeededCount += 1;
        } else {
          failedCount += 1;
        }

        writeStreamEventIfPossible(outputStream, outputWritable, createTargetFinishedEvent(itemView));
      }

      writeStreamEventIfPossible(
        outputStream,
        outputWritable,
        createBatchFinishedEvent(
          normalizedLayer,
          normalizedSelectionType,
          modelNames,
          finishedItems,
          succeededCount,
          failedCount
        )
      );
    };
  }

  @Transactional
  public DefaultScheduledMartsRunResultView runDefaultMartsSchedule() {
    List<DbtModelRunnerClient.RunnerModelItem> targetModels = dbtModelRunnerClient.listModels("marts", null)
      .stream()
      .sorted(Comparator.comparing(DbtModelRunnerClient.RunnerModelItem::name))
      .toList();

    int processedAccounts = 0;
    int skippedAccounts = 0;
    int succeededModels = 0;
    int failedModels = 0;

    for (var account : accountRepository.findAll()) {
      AuthenticatedUser runUser = resolveScheduledRunUser(account.getId());
      if (runUser == null) {
        skippedAccounts += 1;
        continue;
      }

      processedAccounts += 1;
      for (DbtModelRunnerClient.RunnerModelItem targetModel : targetModels) {
        SingleModelRunOutcome outcome = executeSingleModel(runUser, "marts", targetModel.name());
        if (outcome.success()) {
          succeededModels += 1;
        } else {
          failedModels += 1;
        }
      }
    }

    return new DefaultScheduledMartsRunResultView(processedAccounts, skippedAccounts, succeededModels, failedModels);
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

  private String loadLatestSuccessfulRunAt(AuthenticatedUser authenticatedUser, String normalizedLayer, String modelName) {
    Map<String, Instant> latestRuns = loadLatestSuccessfulRuns(
      authenticatedUser,
      normalizedLayer,
      List.of(new DbtModelRunnerClient.RunnerModelItem(modelName, normalizedLayer, null, null, null, null))
    );

    return DateTimeFormats.formatNullableOrNull(latestRuns.get(modelName), zoneId);
  }

  private Long loadEstimatedRowCount(String schemaName, String relationName) {
    String normalizedSchemaName = normalizeNullable(schemaName);
    String normalizedRelationName = normalizeNullable(relationName);
    if (normalizedSchemaName == null || normalizedRelationName == null) {
      return null;
    }

    return jdbcTemplate.query(
      """
        select n_live_tup
        from pg_stat_user_tables
        where schemaname = ?
          and relname = ?
        """,
      ps -> {
        ps.setString(1, normalizedSchemaName);
        ps.setString(2, normalizedRelationName);
      },
      rs -> rs.next() ? rs.getLong(1) : null
    );
  }

  private DbtRunHistoryListItemView toRunHistoryListItemView(DbtRunHistoryEntity entity) {
    return new DbtRunHistoryListItemView(
      entity.getId(),
      entity.getRequestedModelName(),
      entity.getRequestedLayer(),
      entity.getStatus(),
      entity.getReturncode(),
      calculateExecutionTimeSeconds(entity.getStartedAt(), entity.getFinishedAt()),
      DateTimeFormats.formatNullableOrNull(entity.getStartedAt(), zoneId),
      DateTimeFormats.formatNullableOrNull(entity.getFinishedAt(), zoneId)
    );
  }

  private String toConnectorDisplay(String normalizedLayer, String connectorId) {
    if (!"staging".equals(normalizedLayer)) {
      return null;
    }
    String normalizedConnectorId = normalizeNullable(connectorId);
    return normalizedConnectorId == null ? null : connectorService.getConnectorName(normalizedConnectorId);
  }

  private String toDomainDisplay(String normalizedLayer, String domainKey) {
    if ("staging".equals(normalizedLayer)) {
      return null;
    }
    return toDomainDisplayForDetail(domainKey);
  }

  private String toDomainDisplayForDetail(String domainKey) {
    String normalizedDomainKey = normalizeNullable(domainKey);
    if (normalizedDomainKey == null) {
      return null;
    }

    return switch (normalizedDomainKey) {
      case "health" -> "Health";
      case "finance" -> "Finance";
      default -> Character.toUpperCase(normalizedDomainKey.charAt(0)) + normalizedDomainKey.substring(1);
    };
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

  private DbtRunModelHistoryItemView toRunModelHistoryItemView(DbtRunModelHistoryEntity entity) {
    return new DbtRunModelHistoryItemView(
      entity.getModelName(),
      entity.getLayer(),
      entity.getStatus(),
      DateTimeFormats.formatNullableOrNull(entity.getCompletedAt(), zoneId),
      entity.getExecutionTimeSeconds()
    );
  }

  private DbtRunHistoryEntity requireRunHistory(AuthenticatedUser authenticatedUser, UUID runId) {
    return dbtRunHistoryRepository.findByIdAndAccountId(runId, authenticatedUser.accountId())
      .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DBT_RUN_HISTORY_NOT_FOUND", "dbt run history was not found."));
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

  private DbtModelRunnerClient.RunnerModelRunStream openModelRunStreamWithPersistence(
    AuthenticatedUser authenticatedUser,
    String normalizedLayer,
    String modelName
  ) {
    try {
      return dbtModelRunnerClient.openModelRunStream(normalizedLayer, modelName);
    } catch (ApiException exception) {
      if (!"DBT_MODEL_RUN_REQUEST_ERROR".equals(exception.getCode())) {
        persistRunResponseSafely(authenticatedUser, normalizedLayer, modelName, createFailedRunnerResponse(exception));
      }
      throw exception;
    }
  }

  private SingleModelRunOutcome executeSingleModel(
    AuthenticatedUser authenticatedUser,
    String normalizedLayer,
    String modelName
  ) {
    DbtModelRunnerClient.RunnerRunResponse response;
    try {
      response = dbtModelRunnerClient.runModel(normalizedLayer, modelName);
    } catch (ApiException exception) {
      if (!"DBT_MODEL_RUN_REQUEST_ERROR".equals(exception.getCode())) {
        persistRunResponseSafely(
          authenticatedUser,
          normalizedLayer,
          modelName,
          createFailedRunnerResponse(exception)
        );
      }
      return new SingleModelRunOutcome(
        exception.getStatus().value(),
        false,
        null,
        "",
        "",
        null,
        null,
        exception.getCode(),
        exception.getMessage()
      );
    }

    persistRunResponseSafely(authenticatedUser, normalizedLayer, modelName, response);
    return toSingleModelRunOutcome(response);
  }

  private SingleModelRunOutcome toSingleModelRunOutcome(DbtModelRunnerClient.RunnerRunResponse response) {
    return new SingleModelRunOutcome(
      response.statusCode(),
      response.statusCode() == 200 && response.success(),
      response.returncode(),
      response.stdout(),
      response.stderr(),
      formatInstantOrNull(response.startedAt()),
      formatInstantOrNull(response.finishedAt()),
      response.code() != null ? response.code() : (response.statusCode() == 500 ? "DBT_MODEL_RUN_FAILED" : null),
      response.message() != null
        ? response.message()
        : (response.statusCode() == 200 ? "dbt model run completed." : "dbt model run failed.")
    );
  }

  private DbtModelRunnerClient.RunnerRunResponse createFailedRunnerResponse(ApiException exception) {
    return new DbtModelRunnerClient.RunnerRunResponse(
      exception.getStatus().value(),
      false,
      null,
      "",
      "",
      null,
      null,
      exception.getCode(),
      exception.getMessage(),
      List.of()
    );
  }

  private DbtModelRunnerClient.RunnerRunResponse createUnexpectedStreamFailureResponse(Exception exception) {
    return new DbtModelRunnerClient.RunnerRunResponse(
      500,
      false,
      null,
      "",
      "",
      null,
      null,
      "DBT_STREAM_EXECUTION_FAILED",
      "Unable to stream dbt model run output: " + exception.getMessage(),
      List.of()
    );
  }

  private List<DbtModelRunnerClient.RunnerModelItem> resolveSelectedModels(
    String normalizedLayer,
    String normalizedSelectionType,
    List<String> modelNames
  ) {
    List<String> normalizedModelNames = normalizeModelNames(modelNames);
    if (normalizedModelNames.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "At least one model name is required.");
    }

    Map<String, DbtModelRunnerClient.RunnerModelItem> modelsByName = dbtModelRunnerClient.listModels(normalizedLayer, null)
      .stream()
      .collect(
        java.util.stream.Collectors.toMap(
          DbtModelRunnerClient.RunnerModelItem::name,
          item -> item,
          (left, right) -> left,
          LinkedHashMap::new
        )
      );

    List<String> invalidModelNames = normalizedModelNames.stream()
      .filter(modelName -> !modelsByName.containsKey(modelName))
      .toList();

    if (!invalidModelNames.isEmpty()) {
      throw new ApiException(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR",
        "Invalid model names for layer " + normalizedLayer + ": " + String.join(", ", invalidModelNames)
      );
    }

    List<DbtModelRunnerClient.RunnerModelItem> selectedModels = normalizedModelNames.stream()
      .map(modelsByName::get)
      .filter(item -> item != null)
      .distinct()
      .sorted(Comparator.comparing(DbtModelRunnerClient.RunnerModelItem::name))
      .toList();

    if (selectedModels.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "No valid models were selected.");
    }

    return selectedModels;
  }

  private AuthenticatedUser resolveScheduledRunUser(UUID accountId) {
    UserEntity user = userRepository.findFirstByAccountIdAndRoleOrderByCreatedAtAscIdAsc(accountId, "ADMIN")
      .or(() -> userRepository.findFirstByAccountIdOrderByCreatedAtAscIdAsc(accountId))
      .orElse(null);

    if (user == null) {
      return null;
    }

    return new AuthenticatedUser(user.getId(), accountId, user.getPrincipal(), user.getRole());
  }

  private Map<String, Object> createRunStartedEvent(String normalizedLayer, String modelName) {
    return Map.of(
      "type", "run_started",
      "layer", normalizedLayer,
      "modelName", modelName
    );
  }

  private Map<String, Object> createRunFinishedEvent(
    String normalizedLayer,
    String modelName,
    SingleModelRunOutcome outcome
  ) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("type", "run_finished");
    event.put("layer", normalizedLayer);
    event.put("modelName", modelName);
    event.put("success", outcome.success());
    event.put("returncode", outcome.returncode());
    event.put("stdout", outcome.stdout());
    event.put("stderr", outcome.stderr());
    event.put("startedAt", outcome.startedAt());
    event.put("finishedAt", outcome.finishedAt());
    event.put("code", outcome.code());
    event.put("message", outcome.message());
    return event;
  }

  private Map<String, Object> createLogEvent(DbtModelRunnerClient.RunnerLogEvent logEvent, String targetModelName) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("type", "log");
    event.put("stream", logEvent.stream());
    event.put("text", logEvent.text());
    event.put("timestamp", logEvent.timestamp());
    if (targetModelName != null) {
      event.put("targetModelName", targetModelName);
    }
    return event;
  }

  private Map<String, Object> createBatchStartedEvent(
    String normalizedLayer,
    String normalizedSelectionType,
    List<String> modelNames,
    List<DbtModelRunnerClient.RunnerModelItem> targetModels
  ) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("type", "batch_started");
    event.put("layer", normalizedLayer);
    event.put("selectionType", normalizedSelectionType);
    event.put("modelNames", modelNames);
    event.put("totalModels", targetModels.size());
    event.put(
      "items",
      targetModels.stream()
        .map(item -> {
          Map<String, Object> itemView = new LinkedHashMap<>();
          itemView.put("modelName", item.name());
          itemView.put("layer", item.layer());
          itemView.put("scopeKey", resolveScopeKey(normalizedSelectionType, item));
          itemView.put("scopeLabel", resolveScopeLabel(normalizedLayer, normalizedSelectionType, item));
          return itemView;
        })
        .toList()
    );
    return event;
  }

  private Map<String, Object> createTargetStartedEvent(
    String normalizedLayer,
    String modelName,
    String scopeKey,
    String scopeLabel
  ) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("type", "target_started");
    event.put("layer", normalizedLayer);
    event.put("modelName", modelName);
    event.put("scopeKey", scopeKey);
    event.put("scopeLabel", scopeLabel);
    return event;
  }

  private Map<String, Object> createTargetFinishedEvent(DbtBatchModelRunItemView itemView) {
    return Map.of(
      "type", "target_finished",
      "item", itemView
    );
  }

  private Map<String, Object> createBatchFinishedEvent(
    String normalizedLayer,
    String normalizedSelectionType,
    List<String> modelNames,
    List<DbtBatchModelRunItemView> items,
    int succeededCount,
    int failedCount
  ) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("type", "batch_finished");
    event.put("layer", normalizedLayer);
    event.put("selectionType", normalizedSelectionType);
    event.put("modelNames", modelNames);
    event.put("totalModels", items.size());
    event.put("succeededCount", succeededCount);
    event.put("failedCount", failedCount);
    event.put("items", items);
    return event;
  }

  private void writeStreamEventIfPossible(
    OutputStream outputStream,
    AtomicBoolean outputWritable,
    Map<String, Object> event
  ) {
    if (!outputWritable.get()) {
      return;
    }

    try {
      writeStreamEvent(outputStream, event);
    } catch (IOException exception) {
      outputWritable.set(false);
      log.debug("Stopping dbt stream writes after client output failure.", exception);
    }
  }

  private void writeStreamEvent(OutputStream outputStream, Map<String, Object> event) throws IOException {
    outputStream.write(objectMapper.writeValueAsBytes(event));
    outputStream.write('\n');
    outputStream.flush();
  }

  private String normalizeSelectionType(String normalizedLayer, String value) {
    String normalized = normalizeRequired(value, "Selection type is required.").toLowerCase(Locale.ROOT);
    if ("staging".equals(normalizedLayer) && "connector".equals(normalized)) {
      return normalized;
    }
    if (!"staging".equals(normalizedLayer) && "domain".equals(normalized)) {
      return normalized;
    }

    String expected = "staging".equals(normalizedLayer) ? "connector" : "domain";
    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Selection type must be " + expected + " for the requested layer.");
  }

  private List<String> normalizeModelNames(List<String> values) {
    if (values == null) {
      return List.of();
    }

    return values.stream()
      .map(this::normalizeNullable)
      .filter(value -> value != null)
      .distinct()
      .toList();
  }

  private String resolveScopeKey(String normalizedScopeType, DbtModelRunnerClient.RunnerModelItem item) {
    return "connector".equals(normalizedScopeType)
      ? normalizeNullable(item.connectorId())
      : normalizeNullable(item.domainKey());
  }

  private String resolveScopeLabel(String normalizedLayer, String normalizedScopeType, DbtModelRunnerClient.RunnerModelItem item) {
    String scopeKey = resolveScopeKey(normalizedScopeType, item);
    if (scopeKey == null) {
      return null;
    }

    return ("connector".equals(normalizedScopeType)
      ? toConnectorDisplay(normalizedLayer, scopeKey)
      : toDomainDisplayForDetail(scopeKey)) != null
      ? ("connector".equals(normalizedScopeType)
        ? toConnectorDisplay(normalizedLayer, scopeKey)
        : toDomainDisplayForDetail(scopeKey))
      : scopeKey;
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

  private Double calculateExecutionTimeSeconds(Instant startedAt, Instant finishedAt) {
    if (startedAt == null || finishedAt == null || finishedAt.isBefore(startedAt)) {
      return null;
    }

    return Duration.between(startedAt, finishedAt).toMillis() / 1000.0;
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

  private record SingleModelRunOutcome(
    int statusCode,
    boolean success,
    Integer returncode,
    String stdout,
    String stderr,
    String startedAt,
    String finishedAt,
    String code,
    String message
  ) {
  }
}

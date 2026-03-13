package com.otw.adminapi.sync;

import com.otw.adminapi.common.api.ApiException;
import com.otw.adminapi.common.util.DateTimeFormats;
import com.otw.adminapi.connector.ConnectorConfigRepository;
import com.otw.adminapi.connector.ConnectorConfigEntity;
import com.otw.adminapi.connector.ConnectorService;
import com.otw.adminapi.security.AuthenticatedUser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SyncTaskService {
  private static final int DEFAULT_PAGE = 1;
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;
  private static final String DEFAULT_SORT_BY = "createdAt";
  private static final String DEFAULT_SORT_ORDER = "desc";
  private static final Set<String> VALID_STATUSES = Set.of("queued", "running", "success", "failed");
  private static final Set<String> VALID_TRIGGER_TYPES = Set.of("manual", "scheduled");
  private static final Set<String> VALID_DOMAINS = Set.of("health", "finance");

  private final SyncTaskRepository syncTaskRepository;
  private final ConnectorConfigRepository connectorConfigRepository;
  private final ConnectorService connectorService;
  private final ZoneId zoneId;

  public SyncTaskService(
    SyncTaskRepository syncTaskRepository,
    ConnectorConfigRepository connectorConfigRepository,
    ConnectorService connectorService,
    @Value("${app.timezone}") String timezone
  ) {
    this.syncTaskRepository = syncTaskRepository;
    this.connectorConfigRepository = connectorConfigRepository;
    this.connectorService = connectorService;
    this.zoneId = ZoneId.of(timezone);
  }

  public SyncTaskView createManualSyncJob(AuthenticatedUser authenticatedUser, String connectorId, CreateSyncJobRequest request) {
    if (ConnectorService.MEDICAL_REPORT_ID.equals(normalizeKey(connectorId))) {
      throw new ApiException(
        HttpStatus.BAD_REQUEST,
        "INVALID_SYNC_PAYLOAD",
        "Medical Report requires /users/me/connectors/medical-report/sync-jobs."
      );
    }

    ConnectorConfigEntity connector = connectorService.requireConnector(authenticatedUser, connectorId);
    if ("not_configured".equals(connector.getStatus())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CONNECTOR_NOT_CONFIGURED", "Configure this connector before starting a sync.");
    }

    Instant startAt = DateTimeFormats.parseRequired(request.startAt(), zoneId);
    Instant endAt = DateTimeFormats.parseRequired(request.endAt(), zoneId);
    if (!endAt.isAfter(startAt)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "End time must be later than start time.");
    }

    boolean hasRunningTask = syncTaskRepository.existsByAccountIdAndConnectorConfigIdAndStatusIn(
      authenticatedUser.accountId(),
      connector.getId(),
      Set.of("queued", "running")
    );
    if (hasRunningTask) {
      throw new ApiException(HttpStatus.CONFLICT, "SYNC_TASK_CONFLICT", "Another sync task is already queued or running for this connector.");
    }

    SyncTaskEntity entity = new SyncTaskEntity();
    entity.setAccountId(authenticatedUser.accountId());
    entity.setConnectorConfigId(connector.getId());
    entity.setTriggerType("manual");
    entity.setStatus("queued");
    entity.setWindowStartAt(startAt);
    entity.setWindowEndAt(endAt);
    return toView(connectorId, syncTaskRepository.save(entity));
  }

  @Transactional(readOnly = true)
  public SyncJobListResponse listJobs(AuthenticatedUser authenticatedUser, SyncJobListRequest request) {
    QueryOptions queryOptions = parseQueryOptions(request);

    Map<UUID, ConnectorMeta> connectorMetaByConfigId = connectorConfigRepository.findByAccountIdOrderByConnectorId(authenticatedUser.accountId())
      .stream()
      .collect(Collectors.toMap(
        ConnectorConfigEntity::getId,
        entity -> new ConnectorMeta(
          entity.getConnectorId(),
          connectorService.getConnectorName(entity.getConnectorId()),
          normalizeKey(entity.getCategory())
        )
      ));

    List<SyncTaskProjection> allTasks = syncTaskRepository.findByAccountId(authenticatedUser.accountId()).stream()
      .map(entity -> toProjection(entity, connectorMetaByConfigId.get(entity.getConnectorConfigId())))
      .filter(Objects::nonNull)
      .filter(task -> VALID_TRIGGER_TYPES.contains(task.triggerType()))
      .toList();

    List<SyncTaskProjection> searchMatchedAllPeriods = allTasks.stream()
      .filter(task -> matchesSearch(task, queryOptions.search()))
      .toList();

    List<SyncTaskProjection> searchMatchedTasks = searchMatchedAllPeriods.stream()
      .filter(task -> matchesPeriod(task.entity().getStartedAt(), queryOptions.periodStartInclusive(), queryOptions.periodEndExclusive()))
      .toList();

    List<SyncTaskProjection> filteredTasks = searchMatchedTasks.stream()
      .filter(task -> matchesOptional(task.status(), queryOptions.status()))
      .filter(task -> matchesOptional(task.triggerType(), queryOptions.triggerType()))
      .filter(task -> matchesOptional(task.domain(), queryOptions.domain()))
      .sorted((left, right) -> compareProjection(left, right, queryOptions.sortField(), queryOptions.sortOrder()))
      .toList();

    int total = filteredTasks.size();
    int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) queryOptions.pageSize());
    int fromIndex = Math.max(0, (queryOptions.page() - 1) * queryOptions.pageSize());
    int toIndex = Math.min(total, fromIndex + queryOptions.pageSize());
    List<SyncJobListItemView> pageItems = fromIndex >= total
      ? List.of()
      : filteredTasks.subList(fromIndex, toIndex).stream().map(this::toListItem).toList();

    List<SyncTaskProjection> statusFacetBase = searchMatchedTasks.stream()
      .filter(task -> matchesOptional(task.triggerType(), queryOptions.triggerType()))
      .filter(task -> matchesOptional(task.domain(), queryOptions.domain()))
      .toList();
    List<SyncTaskProjection> triggerTypeFacetBase = searchMatchedTasks.stream()
      .filter(task -> matchesOptional(task.status(), queryOptions.status()))
      .filter(task -> matchesOptional(task.domain(), queryOptions.domain()))
      .toList();
    List<SyncTaskProjection> domainFacetBase = searchMatchedTasks.stream()
      .filter(task -> matchesOptional(task.status(), queryOptions.status()))
      .filter(task -> matchesOptional(task.triggerType(), queryOptions.triggerType()))
      .toList();
    List<SyncTaskProjection> periodFacetBase = searchMatchedAllPeriods.stream()
      .filter(task -> matchesOptional(task.status(), queryOptions.status()))
      .filter(task -> matchesOptional(task.triggerType(), queryOptions.triggerType()))
      .filter(task -> matchesOptional(task.domain(), queryOptions.domain()))
      .toList();

    PeriodRange yesterdayRange = resolvePeriodRange(PeriodFilter.YESTERDAY);
    PeriodRange last7DaysRange = resolvePeriodRange(PeriodFilter.LAST_7_DAYS);
    PeriodRange last30DaysRange = resolvePeriodRange(PeriodFilter.LAST_30_DAYS);

    SyncJobFacetsView facets = new SyncJobFacetsView(
      searchMatchedTasks.size(),
      new SyncJobFacetsView.SyncJobStatusFacets(
        countBy(statusFacetBase, task -> "queued".equals(task.status())),
        countBy(statusFacetBase, task -> "running".equals(task.status())),
        countBy(statusFacetBase, task -> "success".equals(task.status())),
        countBy(statusFacetBase, task -> "failed".equals(task.status()))
      ),
      new SyncJobFacetsView.SyncJobTriggerTypeFacets(
        countBy(triggerTypeFacetBase, task -> "manual".equals(task.triggerType())),
        countBy(triggerTypeFacetBase, task -> "scheduled".equals(task.triggerType()))
      ),
      new SyncJobFacetsView.SyncJobDomainFacets(
        countBy(domainFacetBase, task -> "health".equals(task.domain())),
        countBy(domainFacetBase, task -> "finance".equals(task.domain()))
      ),
      new SyncJobFacetsView.SyncJobPeriodFacets(
        countBy(periodFacetBase, task -> matchesPeriod(task.entity().getStartedAt(), yesterdayRange.startInclusive(), yesterdayRange.endExclusive())),
        countBy(periodFacetBase, task -> matchesPeriod(task.entity().getStartedAt(), last7DaysRange.startInclusive(), last7DaysRange.endExclusive())),
        countBy(periodFacetBase, task -> matchesPeriod(task.entity().getStartedAt(), last30DaysRange.startInclusive(), last30DaysRange.endExclusive()))
      )
    );

    return new SyncJobListResponse(
      pageItems,
      new SyncJobListPageView(queryOptions.page(), queryOptions.pageSize(), total, totalPages),
      facets
    );
  }

  @Transactional(readOnly = true)
  public SyncTaskView getJob(AuthenticatedUser authenticatedUser, String connectorId, UUID jobId) {
    ConnectorConfigEntity connector = connectorService.requireConnector(authenticatedUser, connectorId);
    SyncTaskEntity entity = syncTaskRepository.findByIdAndAccountIdAndConnectorConfigId(jobId, authenticatedUser.accountId(), connector.getId())
      .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SYNC_TASK_NOT_FOUND", "Sync job not found."));
    return toView(connectorId, entity);
  }

  private SyncTaskView toView(String connectorId, SyncTaskEntity entity) {
    return new SyncTaskView(
      entity.getId(),
      connectorId,
      entity.getStatus(),
      entity.getTriggerType(),
      DateTimeFormats.formatNullable(entity.getWindowStartAt(), zoneId),
      DateTimeFormats.formatNullable(entity.getWindowEndAt(), zoneId),
      DateTimeFormats.formatNullable(entity.getStartedAt(), zoneId),
      DateTimeFormats.formatNullable(entity.getFinishedAt(), zoneId),
      entity.getFetchedCount(),
      entity.getInsertedCount(),
      entity.getUpdatedCount(),
      entity.getUnchangedCount(),
      entity.getDedupedCount(),
      entity.getErrorMessage(),
      DateTimeFormats.formatNullable(entity.getCreatedAt(), zoneId)
    );
  }

  private SyncTaskProjection toProjection(SyncTaskEntity entity, ConnectorMeta connectorMeta) {
    if (connectorMeta == null) {
      return null;
    }

    String status = normalizeKey(entity.getStatus());
    if (!VALID_STATUSES.contains(status)) {
      return null;
    }

    String domain = normalizeKey(connectorMeta.domain());
    if (!VALID_DOMAINS.contains(domain)) {
      return null;
    }

    return new SyncTaskProjection(
      entity,
      connectorMeta.connectorId(),
      connectorMeta.connectorName(),
      domain,
      status,
      normalizeKey(entity.getTriggerType())
    );
  }

  private SyncJobListItemView toListItem(SyncTaskProjection projection) {
    SyncTaskEntity entity = projection.entity();
    String errorMessage = "failed".equals(projection.status())
      ? normalizeNullable(entity.getErrorMessage())
      : null;

    return new SyncJobListItemView(
      entity.getId(),
      projection.connectorId(),
      projection.connectorName(),
      projection.domain(),
      projection.status(),
      projection.triggerType(),
      DateTimeFormats.formatNullableOrNull(entity.getWindowStartAt(), zoneId),
      DateTimeFormats.formatNullableOrNull(entity.getWindowEndAt(), zoneId),
      DateTimeFormats.formatNullableOrNull(entity.getStartedAt(), zoneId),
      DateTimeFormats.formatNullableOrNull(entity.getFinishedAt(), zoneId),
      entity.getFetchedCount(),
      entity.getInsertedCount(),
      entity.getUpdatedCount(),
      entity.getDedupedCount(),
      errorMessage,
      DateTimeFormats.formatNullableOrNull(entity.getCreatedAt(), zoneId)
    );
  }

  private QueryOptions parseQueryOptions(SyncJobListRequest request) {
    int page = request.page() == null ? DEFAULT_PAGE : request.page();
    int pageSize = request.pageSize() == null ? DEFAULT_PAGE_SIZE : request.pageSize();
    if (page < 1 || pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid pagination parameters.");
    }

    PeriodRange periodRange = resolvePeriodRange(parsePeriod(request.period()));

    String normalizedStatus = normalizeNullable(request.status());
    if (normalizedStatus != null && !VALID_STATUSES.contains(normalizedStatus)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid query parameter.");
    }

    String normalizedTriggerType = normalizeNullable(request.triggerType());
    if (normalizedTriggerType != null && !VALID_TRIGGER_TYPES.contains(normalizedTriggerType)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid query parameter.");
    }

    String normalizedDomain = normalizeNullable(request.domain());
    if (normalizedDomain != null && !VALID_DOMAINS.contains(normalizedDomain)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid query parameter.");
    }

    SortField sortField = parseSortField(request.sortBy());
    SortOrder sortOrder = parseSortOrder(request.sortOrder());

    return new QueryOptions(
      page,
      pageSize,
      normalizeNullable(request.search()),
      periodRange.startInclusive(),
      periodRange.endExclusive(),
      normalizedStatus,
      normalizedTriggerType,
      normalizedDomain,
      sortField,
      sortOrder
    );
  }

  private SortField parseSortField(String sortBy) {
    String value = normalizeKey(sortBy == null || sortBy.isBlank() ? DEFAULT_SORT_BY : sortBy);
    return switch (value) {
      case "createdat" -> SortField.CREATED_AT;
      case "windowstart" -> SortField.WINDOW_START;
      case "windowend" -> SortField.WINDOW_END;
      case "startedat" -> SortField.STARTED_AT;
      case "finishedat" -> SortField.FINISHED_AT;
      default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid query parameter.");
    };
  }

  private SortOrder parseSortOrder(String sortOrder) {
    String value = normalizeKey(sortOrder == null || sortOrder.isBlank() ? DEFAULT_SORT_ORDER : sortOrder);
    return switch (value) {
      case "asc" -> SortOrder.ASC;
      case "desc" -> SortOrder.DESC;
      default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid query parameter.");
    };
  }

  private PeriodFilter parsePeriod(String period) {
    String value = normalizeNullable(period);
    if (value == null) {
      return PeriodFilter.NONE;
    }

    return switch (value) {
      case "yesterday" -> PeriodFilter.YESTERDAY;
      case "last_7_days" -> PeriodFilter.LAST_7_DAYS;
      case "last_30_days" -> PeriodFilter.LAST_30_DAYS;
      default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid query parameter.");
    };
  }

  private PeriodRange resolvePeriodRange(PeriodFilter periodFilter) {
    if (periodFilter == PeriodFilter.NONE) {
      return new PeriodRange(null, null);
    }

    ZonedDateTime now = ZonedDateTime.now(zoneId);
    LocalDate today = now.toLocalDate();
    Instant endExclusive = now.plusNanos(1).toInstant();

    return switch (periodFilter) {
      case YESTERDAY -> new PeriodRange(
        today.minusDays(1).atStartOfDay(zoneId).toInstant(),
        today.atStartOfDay(zoneId).toInstant()
      );
      case LAST_7_DAYS -> new PeriodRange(
        today.minusDays(6).atStartOfDay(zoneId).toInstant(),
        endExclusive
      );
      case LAST_30_DAYS -> new PeriodRange(
        today.minusDays(29).atStartOfDay(zoneId).toInstant(),
        endExclusive
      );
      case NONE -> new PeriodRange(null, null);
    };
  }

  private int compareProjection(
    SyncTaskProjection left,
    SyncTaskProjection right,
    SortField sortField,
    SortOrder sortOrder
  ) {
    int result = compareInstant(resolveSortInstant(left.entity(), sortField), resolveSortInstant(right.entity(), sortField), sortOrder);
    if (result != 0) {
      return result;
    }

    int createdAtTieBreak = compareInstant(left.entity().getCreatedAt(), right.entity().getCreatedAt(), SortOrder.DESC);
    if (createdAtTieBreak != 0) {
      return createdAtTieBreak;
    }

    return left.entity().getId().compareTo(right.entity().getId());
  }

  private Instant resolveSortInstant(SyncTaskEntity entity, SortField sortField) {
    return switch (sortField) {
      case CREATED_AT -> entity.getCreatedAt();
      case WINDOW_START -> entity.getWindowStartAt();
      case WINDOW_END -> entity.getWindowEndAt();
      case STARTED_AT -> entity.getStartedAt();
      case FINISHED_AT -> entity.getFinishedAt();
    };
  }

  private int compareInstant(Instant left, Instant right, SortOrder sortOrder) {
    if (left == null && right == null) {
      return 0;
    }
    if (left == null) {
      return 1;
    }
    if (right == null) {
      return -1;
    }

    int result = left.compareTo(right);
    return sortOrder == SortOrder.ASC ? result : -result;
  }

  private boolean matchesSearch(SyncTaskProjection task, String search) {
    if (search == null) {
      return true;
    }

    return normalizeKey(task.connectorName()).contains(search);
  }

  private boolean matchesPeriod(Instant startedAt, Instant periodStartInclusive, Instant periodEndExclusive) {
    if (periodStartInclusive == null || periodEndExclusive == null) {
      return true;
    }

    if (startedAt == null) {
      return false;
    }

    return !startedAt.isBefore(periodStartInclusive) && startedAt.isBefore(periodEndExclusive);
  }

  private boolean matchesOptional(String actual, String expected) {
    return expected == null || Objects.equals(actual, expected);
  }

  private int countBy(List<SyncTaskProjection> source, Predicate<SyncTaskProjection> matcher) {
    return (int) source.stream().filter(matcher).count();
  }

  private String normalizeKey(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private String normalizeNullable(String value) {
    String normalized = normalizeKey(value);
    return normalized.isBlank() ? null : normalized;
  }

  private enum SortField {
    CREATED_AT,
    WINDOW_START,
    WINDOW_END,
    STARTED_AT,
    FINISHED_AT
  }

  private enum SortOrder {
    ASC,
    DESC
  }

  private enum PeriodFilter {
    NONE,
    YESTERDAY,
    LAST_7_DAYS,
    LAST_30_DAYS
  }

  private record ConnectorMeta(
    String connectorId,
    String connectorName,
    String domain
  ) {
  }

  private record SyncTaskProjection(
    SyncTaskEntity entity,
    String connectorId,
    String connectorName,
    String domain,
    String status,
    String triggerType
  ) {
  }

  private record QueryOptions(
    int page,
    int pageSize,
    String search,
    Instant periodStartInclusive,
    Instant periodEndExclusive,
    String status,
    String triggerType,
    String domain,
    SortField sortField,
    SortOrder sortOrder
  ) {
  }

  private record PeriodRange(
    Instant startInclusive,
    Instant endExclusive
  ) {
  }
}

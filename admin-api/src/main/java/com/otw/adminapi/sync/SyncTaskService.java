package com.otw.adminapi.sync;

import com.otw.adminapi.common.api.ApiException;
import com.otw.adminapi.common.util.DateTimeFormats;
import com.otw.adminapi.connector.ConnectorConfigEntity;
import com.otw.adminapi.connector.ConnectorService;
import com.otw.adminapi.security.AuthenticatedUser;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SyncTaskService {
  private final SyncTaskRepository syncTaskRepository;
  private final ConnectorService connectorService;
  private final ZoneId zoneId;

  public SyncTaskService(
    SyncTaskRepository syncTaskRepository,
    ConnectorService connectorService,
    @Value("${app.timezone}") String timezone
  ) {
    this.syncTaskRepository = syncTaskRepository;
    this.connectorService = connectorService;
    this.zoneId = ZoneId.of(timezone);
  }

  public SyncTaskView createManualSyncJob(AuthenticatedUser authenticatedUser, String connectorId, CreateSyncJobRequest request) {
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
  public List<SyncTaskView> listJobs(AuthenticatedUser authenticatedUser) {
    return syncTaskRepository.findTop50ByAccountIdOrderByCreatedAtDesc(authenticatedUser.accountId()).stream()
      .map(entity -> {
        String connectorId = connectorService.requireConnector(authenticatedUser, "garmin-connect").getConnectorId();
        return toView(connectorId, entity);
      })
      .toList();
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
}

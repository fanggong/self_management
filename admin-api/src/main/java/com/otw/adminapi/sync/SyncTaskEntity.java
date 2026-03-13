package com.otw.adminapi.sync;

import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sync_task", schema = "app")
public class SyncTaskEntity {
  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false)
  private UUID accountId;

  @Column(nullable = false)
  private UUID connectorConfigId;

  @Column
  private UUID parseSessionId;

  @Column(nullable = false, length = 32)
  private String triggerType;

  @Column(nullable = false, length = 32)
  private String status;

  @Column
  private Instant windowStartAt;

  @Column
  private Instant windowEndAt;

  @Column
  private Instant startedAt;

  @Column
  private Instant finishedAt;

  @Column(nullable = false)
  private Integer fetchedCount = 0;

  @Column(nullable = false)
  private Integer insertedCount = 0;

  @Column(nullable = false)
  private Integer updatedCount = 0;

  @Column(nullable = false)
  private Integer unchangedCount = 0;

  @Column(nullable = false)
  private Integer dedupedCount = 0;

  @Column(length = 64)
  private String errorCode;

  @Column(columnDefinition = "TEXT")
  private String errorMessage;

  @Column
  private Instant dispatchedAt;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updatedAt;

  public UUID getId() {
    return id;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public void setAccountId(UUID accountId) {
    this.accountId = accountId;
  }

  public UUID getConnectorConfigId() {
    return connectorConfigId;
  }

  public void setConnectorConfigId(UUID connectorConfigId) {
    this.connectorConfigId = connectorConfigId;
  }

  public UUID getParseSessionId() {
    return parseSessionId;
  }

  public void setParseSessionId(UUID parseSessionId) {
    this.parseSessionId = parseSessionId;
  }

  public String getTriggerType() {
    return triggerType;
  }

  public void setTriggerType(String triggerType) {
    this.triggerType = triggerType;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getWindowStartAt() {
    return windowStartAt;
  }

  public void setWindowStartAt(Instant windowStartAt) {
    this.windowStartAt = windowStartAt;
  }

  public Instant getWindowEndAt() {
    return windowEndAt;
  }

  public void setWindowEndAt(Instant windowEndAt) {
    this.windowEndAt = windowEndAt;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(Instant finishedAt) {
    this.finishedAt = finishedAt;
  }

  public Integer getFetchedCount() {
    return fetchedCount;
  }

  public void setFetchedCount(Integer fetchedCount) {
    this.fetchedCount = fetchedCount;
  }

  public Integer getInsertedCount() {
    return insertedCount;
  }

  public void setInsertedCount(Integer insertedCount) {
    this.insertedCount = insertedCount;
  }

  public Integer getUpdatedCount() {
    return updatedCount;
  }

  public void setUpdatedCount(Integer updatedCount) {
    this.updatedCount = updatedCount;
  }

  public Integer getUnchangedCount() {
    return unchangedCount;
  }

  public void setUnchangedCount(Integer unchangedCount) {
    this.unchangedCount = unchangedCount;
  }

  public Integer getDedupedCount() {
    return dedupedCount;
  }

  public void setDedupedCount(Integer dedupedCount) {
    this.dedupedCount = dedupedCount;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Instant getDispatchedAt() {
    return dispatchedAt;
  }

  public void setDispatchedAt(Instant dispatchedAt) {
    this.dispatchedAt = dispatchedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

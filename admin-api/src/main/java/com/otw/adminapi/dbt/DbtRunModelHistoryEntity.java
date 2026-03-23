package com.otw.adminapi.dbt;

import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "dbt_run_model_history", schema = "app")
public class DbtRunModelHistoryEntity {
  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false)
  private UUID dbtRunHistoryId;

  @Column(nullable = false)
  private UUID accountId;

  @Column(nullable = false, length = 32)
  private String layer;

  @Column(nullable = false, length = 255)
  private String modelName;

  @Column(nullable = false, length = 255)
  private String uniqueId;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(columnDefinition = "TEXT")
  private String message;

  @Column(columnDefinition = "TEXT")
  private String relationName;

  @Column
  private Double executionTimeSeconds;

  @Column
  private Instant completedAt;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  public UUID getId() {
    return id;
  }

  public UUID getDbtRunHistoryId() {
    return dbtRunHistoryId;
  }

  public void setDbtRunHistoryId(UUID dbtRunHistoryId) {
    this.dbtRunHistoryId = dbtRunHistoryId;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public void setAccountId(UUID accountId) {
    this.accountId = accountId;
  }

  public String getLayer() {
    return layer;
  }

  public void setLayer(String layer) {
    this.layer = layer;
  }

  public String getModelName() {
    return modelName;
  }

  public void setModelName(String modelName) {
    this.modelName = modelName;
  }

  public String getUniqueId() {
    return uniqueId;
  }

  public void setUniqueId(String uniqueId) {
    this.uniqueId = uniqueId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getRelationName() {
    return relationName;
  }

  public void setRelationName(String relationName) {
    this.relationName = relationName;
  }

  public Double getExecutionTimeSeconds() {
    return executionTimeSeconds;
  }

  public void setExecutionTimeSeconds(Double executionTimeSeconds) {
    this.executionTimeSeconds = executionTimeSeconds;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

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
@Table(name = "dbt_run_history", schema = "app")
public class DbtRunHistoryEntity {
  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false)
  private UUID accountId;

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 32)
  private String requestedLayer;

  @Column(nullable = false, length = 255)
  private String requestedModelName;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String selector;

  @Column(nullable = false, length = 32)
  private String status;

  @Column
  private Integer returncode;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String stdout = "";

  @Column(nullable = false, columnDefinition = "TEXT")
  private String stderr = "";

  @Column(length = 64)
  private String errorCode;

  @Column(columnDefinition = "TEXT")
  private String errorMessage;

  @Column
  private Instant startedAt;

  @Column
  private Instant finishedAt;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  public UUID getId() {
    return id;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public void setAccountId(UUID accountId) {
    this.accountId = accountId;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getRequestedLayer() {
    return requestedLayer;
  }

  public void setRequestedLayer(String requestedLayer) {
    this.requestedLayer = requestedLayer;
  }

  public String getRequestedModelName() {
    return requestedModelName;
  }

  public void setRequestedModelName(String requestedModelName) {
    this.requestedModelName = requestedModelName;
  }

  public String getSelector() {
    return selector;
  }

  public void setSelector(String selector) {
    this.selector = selector;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Integer getReturncode() {
    return returncode;
  }

  public void setReturncode(Integer returncode) {
    this.returncode = returncode;
  }

  public String getStdout() {
    return stdout;
  }

  public void setStdout(String stdout) {
    this.stdout = stdout;
  }

  public String getStderr() {
    return stderr;
  }

  public void setStderr(String stderr) {
    this.stderr = stderr;
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

  public Instant getCreatedAt() {
    return createdAt;
  }
}

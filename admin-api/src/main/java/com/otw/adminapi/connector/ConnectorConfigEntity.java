package com.otw.adminapi.connector;

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
@Table(name = "connector_config", schema = "app")
public class ConnectorConfigEntity {
  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false)
  private UUID accountId;

  @Column(nullable = false, length = 120)
  private String connectorId;

  @Column(nullable = false, length = 64)
  private String category;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(nullable = false, length = 64)
  private String schedule;

  @Column
  private Instant lastRunAt;

  @Column
  private Instant nextRunAt;

  @Column(columnDefinition = "TEXT")
  private String configCiphertext;

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

  public String getConnectorId() {
    return connectorId;
  }

  public void setConnectorId(String connectorId) {
    this.connectorId = connectorId;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getSchedule() {
    return schedule;
  }

  public void setSchedule(String schedule) {
    this.schedule = schedule;
  }

  public Instant getLastRunAt() {
    return lastRunAt;
  }

  public void setLastRunAt(Instant lastRunAt) {
    this.lastRunAt = lastRunAt;
  }

  public Instant getNextRunAt() {
    return nextRunAt;
  }

  public void setNextRunAt(Instant nextRunAt) {
    this.nextRunAt = nextRunAt;
  }

  public String getConfigCiphertext() {
    return configCiphertext;
  }

  public void setConfigCiphertext(String configCiphertext) {
    this.configCiphertext = configCiphertext;
  }
}

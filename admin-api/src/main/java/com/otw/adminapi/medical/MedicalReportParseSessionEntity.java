package com.otw.adminapi.medical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "medical_report_parse_session", schema = "app")
public class MedicalReportParseSessionEntity {
  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false)
  private UUID accountId;

  @Column(nullable = false)
  private UUID connectorConfigId;

  @Column(nullable = false, length = 64)
  private String provider;

  @Column(nullable = false, length = 128)
  private String modelId;

  @Column(nullable = false, length = 128)
  private String recordNumber;

  @Column(nullable = false)
  private LocalDate reportDate;

  @Column(nullable = false, length = 255)
  private String institution;

  @Column(nullable = false, length = 255)
  private String fileName;

  @Column(nullable = false, length = 128)
  private String fileHash;

  @Column(nullable = false, columnDefinition = "jsonb")
  @ColumnTransformer(write = "?::jsonb")
  private String parsedPayloadJsonb;

  @Column(columnDefinition = "jsonb")
  @ColumnTransformer(write = "?::jsonb")
  private String confirmedPayloadJsonb;

  @Column(nullable = false, length = 32)
  private String status = "parsed";

  @Column(nullable = false)
  private Instant expiresAt;

  @Column
  private Instant consumedAt;

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

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getModelId() {
    return modelId;
  }

  public void setModelId(String modelId) {
    this.modelId = modelId;
  }

  public String getRecordNumber() {
    return recordNumber;
  }

  public void setRecordNumber(String recordNumber) {
    this.recordNumber = recordNumber;
  }

  public LocalDate getReportDate() {
    return reportDate;
  }

  public void setReportDate(LocalDate reportDate) {
    this.reportDate = reportDate;
  }

  public String getInstitution() {
    return institution;
  }

  public void setInstitution(String institution) {
    this.institution = institution;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getFileHash() {
    return fileHash;
  }

  public void setFileHash(String fileHash) {
    this.fileHash = fileHash;
  }

  public String getParsedPayloadJsonb() {
    return parsedPayloadJsonb;
  }

  public void setParsedPayloadJsonb(String parsedPayloadJsonb) {
    this.parsedPayloadJsonb = parsedPayloadJsonb;
  }

  public String getConfirmedPayloadJsonb() {
    return confirmedPayloadJsonb;
  }

  public void setConfirmedPayloadJsonb(String confirmedPayloadJsonb) {
    this.confirmedPayloadJsonb = confirmedPayloadJsonb;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Instant getConsumedAt() {
    return consumedAt;
  }

  public void setConsumedAt(Instant consumedAt) {
    this.consumedAt = consumedAt;
  }
}

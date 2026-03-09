package com.otw.adminapi.connector;

import com.otw.adminapi.common.api.ApiException;
import com.otw.adminapi.common.util.DateTimeFormats;
import com.otw.adminapi.security.AuthenticatedUser;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ConnectorService {
  private static final String GARMIN_CONNECT_ID = "garmin-connect";

  private final ConnectorConfigRepository connectorConfigRepository;
  private final CryptoService cryptoService;
  private final CronScheduleService cronScheduleService;
  private final ConnectorVerificationClient connectorVerificationClient;
  private final ZoneId zoneId;

  public ConnectorService(
    ConnectorConfigRepository connectorConfigRepository,
    CryptoService cryptoService,
    CronScheduleService cronScheduleService,
    ConnectorVerificationClient connectorVerificationClient,
    @Value("${app.timezone}") String timezone
  ) {
    this.connectorConfigRepository = connectorConfigRepository;
    this.cryptoService = cryptoService;
    this.cronScheduleService = cronScheduleService;
    this.connectorVerificationClient = connectorVerificationClient;
    this.zoneId = ZoneId.of(timezone);
  }

  @Transactional(readOnly = true)
  public List<ConnectorCatalogItemView> listCatalog() {
    return List.of(
      new ConnectorCatalogItemView(
        GARMIN_CONNECT_ID,
        "Garmin Connect",
        "health",
        List.of(
          new ConnectorConfigFieldView("username", "Username", "text", "Enter your Garmin Connect username", true, "username"),
          new ConnectorConfigFieldView("password", "Password", "password", "Enter your Garmin Connect password", true, "current-password")
        )
      )
    );
  }

  @Transactional(readOnly = true)
  public List<ConnectorRecordView> listConnectors(AuthenticatedUser authenticatedUser) {
    return connectorConfigRepository.findByAccountIdOrderByConnectorId(authenticatedUser.accountId()).stream()
      .map(this::toView)
      .toList();
  }

  public void testConnection(AuthenticatedUser authenticatedUser, String connectorId, TestConnectionRequest request) {
    validateConnectorId(connectorId);
    Map<String, String> normalizedConfig = normalizeConfig(request.config());
    validateConfig(normalizedConfig);
    connectorVerificationClient.verifyGarminConnection(normalizedConfig);
  }

  public ConnectorRecordView saveConfiguration(AuthenticatedUser authenticatedUser, String connectorId, SaveConnectorConfigurationRequest request) {
    validateConnectorId(connectorId);
    Map<String, String> normalizedConfig = normalizeConfig(request.config());
    validateConfig(normalizedConfig);
    connectorVerificationClient.verifyGarminConnection(normalizedConfig);

    ConnectorConfigEntity connector = requireConnector(authenticatedUser, connectorId);
    connector.setSchedule(request.schedule().trim());
    connector.setNextRunAt(cronScheduleService.nextRun(request.schedule().trim(), zoneId));
    connector.setConfigCiphertext(cryptoService.encrypt(normalizedConfig));
    connector.setStatus("stopped");
    return toView(connectorConfigRepository.save(connector));
  }

  public ConnectorRecordView updateStatus(AuthenticatedUser authenticatedUser, String connectorId, UpdateConnectorStatusRequest request) {
    validateConnectorId(connectorId);
    String nextStatus = request.status().trim().toLowerCase(Locale.ROOT);
    if (!List.of("running", "stopped").contains(nextStatus)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Status must be running or stopped.");
    }

    ConnectorConfigEntity connector = requireConnector(authenticatedUser, connectorId);
    if (connector.getConfigCiphertext() == null || connector.getConfigCiphertext().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CONNECTOR_NOT_CONFIGURED", "Configure this connector before changing its status.");
    }

    connector.setStatus(nextStatus);
    return toView(connectorConfigRepository.save(connector));
  }

  @Transactional(readOnly = true)
  public ConnectorConfigEntity requireConnector(AuthenticatedUser authenticatedUser, String connectorId) {
    return connectorConfigRepository.findByAccountIdAndConnectorId(authenticatedUser.accountId(), connectorId)
      .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONNECTOR_NOT_FOUND", "Connector not found."));
  }

  private void validateConnectorId(String connectorId) {
    if (!GARMIN_CONNECT_ID.equals(connectorId)) {
      throw new ApiException(HttpStatus.NOT_FOUND, "CONNECTOR_NOT_FOUND", "Connector not found.");
    }
  }

  private void validateConfig(Map<String, String> config) {
    String username = String.valueOf(config == null ? "" : config.getOrDefault("username", "")).trim();
    String password = String.valueOf(config == null ? "" : config.getOrDefault("password", ""));

    if (username.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CONNECTOR_VALIDATION_ERROR", "Username is required.");
    }

    if (password.length() < 6) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CONNECTOR_VALIDATION_ERROR", "Password must be at least 6 characters.");
    }
  }

  private Map<String, String> normalizeConfig(Map<String, String> config) {
    if (config == null) {
      return Map.of(
        "username", "",
        "password", ""
      );
    }

    return Map.of(
      "username", String.valueOf(config.getOrDefault("username", "")).trim(),
      "password", String.valueOf(config.getOrDefault("password", ""))
    );
  }

  private ConnectorRecordView toView(ConnectorConfigEntity entity) {
    return new ConnectorRecordView(
      entity.getId(),
      entity.getConnectorId(),
      "Garmin Connect",
      entity.getCategory(),
      entity.getStatus(),
      entity.getSchedule(),
      DateTimeFormats.formatNullable(entity.getLastRunAt(), zoneId),
      DateTimeFormats.formatNullable(entity.getNextRunAt(), zoneId),
      cryptoService.decrypt(entity.getConfigCiphertext())
    );
  }
}

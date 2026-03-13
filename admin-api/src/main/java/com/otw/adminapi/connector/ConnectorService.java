package com.otw.adminapi.connector;

import com.otw.adminapi.common.api.ApiException;
import com.otw.adminapi.common.util.DateTimeFormats;
import com.otw.adminapi.security.AuthenticatedUser;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ConnectorService {
  public static final String GARMIN_CONNECT_ID = "garmin-connect";
  public static final String MEDICAL_REPORT_ID = "medical-report";

  private static final Set<String> VALID_CONNECTOR_IDS = Set.of(GARMIN_CONNECT_ID, MEDICAL_REPORT_ID);
  private static final Set<String> MANUAL_ONLY_CONNECTOR_IDS = Set.of(MEDICAL_REPORT_ID);
  private static final Set<String> MEDICAL_REPORT_PROVIDERS = Set.of("deepseek", "volcengine");
  private static final Map<String, String> CONNECTOR_NAME_MAP = Map.of(
    GARMIN_CONNECT_ID, "Garmin Connect",
    MEDICAL_REPORT_ID, "Medical Report"
  );

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
        getConnectorName(GARMIN_CONNECT_ID),
        "health",
        List.of(
          new ConnectorConfigFieldView("username", "Username", "text", "Enter your Garmin Connect username", true, "username"),
          new ConnectorConfigFieldView("password", "Password", "password", "Enter your Garmin Connect password", true, "current-password")
        )
      ),
      new ConnectorCatalogItemView(
        MEDICAL_REPORT_ID,
        getConnectorName(MEDICAL_REPORT_ID),
        "health",
        List.of(
          new ConnectorConfigFieldView("provider", "Provider", "select", "Select provider", true, "off"),
          new ConnectorConfigFieldView("modelId", "Model ID", "text", "Enter model ID", true, "off"),
          new ConnectorConfigFieldView("apiKey", "API Key", "password", "Enter API key", true, "off")
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
    String normalizedConnectorId = requireSupportedConnectorId(connectorId);
    Map<String, String> normalizedConfig = normalizeConfig(normalizedConnectorId, request.config());
    validateConfig(normalizedConnectorId, normalizedConfig);
    verifyConnection(normalizedConnectorId, normalizedConfig);
  }

  public ConnectorRecordView saveConfiguration(AuthenticatedUser authenticatedUser, String connectorId, SaveConnectorConfigurationRequest request) {
    String normalizedConnectorId = requireSupportedConnectorId(connectorId);
    Map<String, String> normalizedConfig = normalizeConfig(normalizedConnectorId, request.config());
    validateConfig(normalizedConnectorId, normalizedConfig);
    verifyConnection(normalizedConnectorId, normalizedConfig);

    ConnectorConfigEntity connector = requireConnector(authenticatedUser, normalizedConnectorId);
    String normalizedSchedule = normalizeSchedule(normalizedConnectorId, request.schedule());
    connector.setSchedule(normalizedSchedule);
    connector.setNextRunAt(isManualOnlyConnector(normalizedConnectorId)
      ? null
      : cronScheduleService.nextRun(normalizedSchedule, zoneId));
    connector.setConfigCiphertext(cryptoService.encrypt(normalizedConfig));
    connector.setStatus("stopped");
    return toView(connectorConfigRepository.save(connector));
  }

  public ConnectorRecordView updateStatus(AuthenticatedUser authenticatedUser, String connectorId, UpdateConnectorStatusRequest request) {
    String normalizedConnectorId = requireSupportedConnectorId(connectorId);
    String nextStatus = normalizeKey(request.status());
    if (!List.of("running", "stopped").contains(nextStatus)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Status must be running or stopped.");
    }

    ConnectorConfigEntity connector = requireConnector(authenticatedUser, normalizedConnectorId);
    if (connector.getConfigCiphertext() == null || connector.getConfigCiphertext().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CONNECTOR_NOT_CONFIGURED", "Configure this connector before changing its status.");
    }

    connector.setStatus(nextStatus);
    return toView(connectorConfigRepository.save(connector));
  }

  @Transactional(readOnly = true)
  public ConnectorConfigEntity requireConnector(AuthenticatedUser authenticatedUser, String connectorId) {
    String normalizedConnectorId = requireSupportedConnectorId(connectorId);
    return connectorConfigRepository.findByAccountIdAndConnectorId(authenticatedUser.accountId(), normalizedConnectorId)
      .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONNECTOR_NOT_FOUND", "Connector not found."));
  }

  @Transactional(readOnly = true)
  public ConnectorConfigEntity requireConfiguredConnector(AuthenticatedUser authenticatedUser, String connectorId) {
    ConnectorConfigEntity connector = requireConnector(authenticatedUser, connectorId);
    if (connector.getConfigCiphertext() == null || connector.getConfigCiphertext().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CONNECTOR_NOT_CONFIGURED", "Please configure this connector first.");
    }
    return connector;
  }

  public String getConnectorName(String connectorId) {
    String normalizedConnectorId = normalizeKey(connectorId);
    return CONNECTOR_NAME_MAP.getOrDefault(normalizedConnectorId, normalizedConnectorId);
  }

  public boolean isManualOnlyConnector(String connectorId) {
    return MANUAL_ONLY_CONNECTOR_IDS.contains(normalizeKey(connectorId));
  }

  private String requireSupportedConnectorId(String connectorId) {
    String normalizedConnectorId = normalizeKey(connectorId);
    if (!VALID_CONNECTOR_IDS.contains(normalizedConnectorId)) {
      throw new ApiException(HttpStatus.NOT_FOUND, "CONNECTOR_NOT_FOUND", "Connector not found.");
    }
    return normalizedConnectorId;
  }

  private String normalizeSchedule(String connectorId, String schedule) {
    if (isManualOnlyConnector(connectorId)) {
      return "-";
    }

    String normalized = schedule == null ? "" : schedule.trim();
    if (normalized.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Update frequency is required.");
    }
    return normalized;
  }

  private void validateConfig(String connectorId, Map<String, String> config) {
    if (GARMIN_CONNECT_ID.equals(connectorId)) {
      String username = String.valueOf(config.getOrDefault("username", "")).trim();
      String password = String.valueOf(config.getOrDefault("password", ""));

      if (username.isBlank()) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "CONNECTOR_VALIDATION_ERROR", "Username is required.");
      }

      if (password.length() < 6) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "CONNECTOR_VALIDATION_ERROR", "Password must be at least 6 characters.");
      }
      return;
    }

    String provider = String.valueOf(config.getOrDefault("provider", "")).trim().toLowerCase(Locale.ROOT);
    String modelId = String.valueOf(config.getOrDefault("modelId", "")).trim();
    String apiKey = String.valueOf(config.getOrDefault("apiKey", "")).trim();

    if (!MEDICAL_REPORT_PROVIDERS.contains(provider)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CONNECTOR_VALIDATION_ERROR", "Provider must be DeepSeek or Volcengine.");
    }
    if (modelId.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CONNECTOR_VALIDATION_ERROR", "Model ID is required.");
    }
    if (apiKey.length() < 8) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CONNECTOR_VALIDATION_ERROR", "API Key is required.");
    }
  }

  private Map<String, String> normalizeConfig(String connectorId, Map<String, String> config) {
    if (GARMIN_CONNECT_ID.equals(connectorId)) {
      if (config == null) {
        return Map.of("username", "", "password", "");
      }
      return Map.of(
        "username", String.valueOf(config.getOrDefault("username", "")).trim(),
        "password", String.valueOf(config.getOrDefault("password", ""))
      );
    }

    if (config == null) {
      return Map.of("provider", "", "modelId", "", "apiKey", "");
    }

    return Map.of(
      "provider", String.valueOf(config.getOrDefault("provider", "")).trim().toLowerCase(Locale.ROOT),
      "modelId", String.valueOf(config.getOrDefault("modelId", "")).trim(),
      "apiKey", String.valueOf(config.getOrDefault("apiKey", "")).trim()
    );
  }

  private void verifyConnection(String connectorId, Map<String, String> config) {
    if (GARMIN_CONNECT_ID.equals(connectorId)) {
      connectorVerificationClient.verifyGarminConnection(config);
      return;
    }

    connectorVerificationClient.verifyMedicalReportConnection(config);
  }

  private String normalizeKey(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private ConnectorRecordView toView(ConnectorConfigEntity entity) {
    return new ConnectorRecordView(
      entity.getId(),
      entity.getConnectorId(),
      getConnectorName(entity.getConnectorId()),
      entity.getCategory(),
      entity.getStatus(),
      entity.getSchedule(),
      DateTimeFormats.formatNullable(entity.getLastRunAt(), zoneId),
      DateTimeFormats.formatNullable(entity.getNextRunAt(), zoneId),
      cryptoService.decrypt(entity.getConfigCiphertext())
    );
  }
}

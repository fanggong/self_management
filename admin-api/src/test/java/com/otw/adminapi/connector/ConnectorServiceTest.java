package com.otw.adminapi.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.otw.adminapi.security.AuthenticatedUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ConnectorServiceTest {
  @Mock
  private ConnectorConfigRepository connectorConfigRepository;

  @Mock
  private CryptoService cryptoService;

  @Mock
  private CronScheduleService cronScheduleService;

  @Mock
  private ConnectorVerificationClient connectorVerificationClient;

  private ConnectorService service;
  private AuthenticatedUser authenticatedUser;

  @BeforeEach
  void setUp() {
    service = new ConnectorService(
      connectorConfigRepository,
      cryptoService,
      cronScheduleService,
      connectorVerificationClient,
      "Asia/Shanghai"
    );
    authenticatedUser = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "demo", "USER");
  }

  @Test
  void listConnectorsOmitsSensitiveValuesFromView() {
    ConnectorConfigEntity connector = createConnector("garmin-connect", "encrypted-garmin");
    when(connectorConfigRepository.findByAccountIdOrderByConnectorId(authenticatedUser.accountId()))
      .thenReturn(List.of(connector));
    when(cryptoService.decrypt("encrypted-garmin"))
      .thenReturn(Map.of("username", "alice", "password", "stored-password"));

    List<ConnectorRecordView> views = service.listConnectors(authenticatedUser);

    assertEquals(1, views.size());
    ConnectorRecordView view = views.getFirst();
    assertEquals("alice", view.config().get("username"));
    assertFalse(view.config().containsKey("password"));
    assertEquals(true, view.secretFieldsConfigured().get("password"));
  }

  @Test
  void saveConfigurationReusesStoredSecretWhenRequestLeavesItBlank() {
    ConnectorConfigEntity connector = createConnector("garmin-connect", "encrypted-garmin");
    when(connectorConfigRepository.findByAccountIdAndConnectorId(authenticatedUser.accountId(), ConnectorService.GARMIN_CONNECT_ID))
      .thenReturn(Optional.of(connector));
    when(cryptoService.decrypt("encrypted-garmin"))
      .thenReturn(Map.of("username", "alice", "password", "stored-password"));
    when(cronScheduleService.nextRun("0 3 * * *", java.time.ZoneId.of("Asia/Shanghai")))
      .thenReturn(Instant.parse("2026-03-29T19:00:00Z"));
    when(cryptoService.encrypt(any())).thenReturn("encrypted-updated");
    when(connectorConfigRepository.save(any(ConnectorConfigEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ConnectorRecordView view = service.saveConfiguration(
      authenticatedUser,
      ConnectorService.GARMIN_CONNECT_ID,
      new SaveConnectorConfigurationRequest("0 3 * * *", Map.of("username", "alice", "password", ""))
    );

    verify(connectorVerificationClient).verifyGarminConnection(
      Map.of("username", "alice", "password", "stored-password")
    );
    assertEquals("encrypted-updated", connector.getConfigCiphertext());
    assertNotNull(connector.getNextRunAt());
    assertEquals("alice", view.config().get("username"));
    assertFalse(view.config().containsKey("password"));
    assertTrue(view.secretFieldsConfigured().get("password"));
  }

  private ConnectorConfigEntity createConnector(String connectorId, String ciphertext) {
    ConnectorConfigEntity entity = new ConnectorConfigEntity();
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    entity.setAccountId(authenticatedUser.accountId());
    entity.setConnectorId(connectorId);
    entity.setCategory("health");
    entity.setStatus("stopped");
    entity.setSchedule("0 2 * * *");
    entity.setConfigCiphertext(ciphertext);
    return entity;
  }
}

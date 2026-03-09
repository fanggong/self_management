package com.otw.adminapi.connector;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectorConfigRepository extends JpaRepository<ConnectorConfigEntity, UUID> {
  List<ConnectorConfigEntity> findByAccountIdOrderByConnectorId(UUID accountId);

  Optional<ConnectorConfigEntity> findByAccountIdAndConnectorId(UUID accountId, String connectorId);
}

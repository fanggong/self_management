package com.otw.adminapi.sync;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncTaskRepository extends JpaRepository<SyncTaskEntity, UUID> {
  boolean existsByAccountIdAndConnectorConfigIdAndStatusIn(UUID accountId, UUID connectorConfigId, Collection<String> statuses);

  List<SyncTaskEntity> findTop50ByAccountIdOrderByCreatedAtDesc(UUID accountId);

  Optional<SyncTaskEntity> findByIdAndAccountIdAndConnectorConfigId(UUID id, UUID accountId, UUID connectorConfigId);
}

package com.otw.adminapi.medical;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalReportParseSessionRepository extends JpaRepository<MedicalReportParseSessionEntity, UUID> {
  Optional<MedicalReportParseSessionEntity> findByIdAndAccountId(UUID id, UUID accountId);
}

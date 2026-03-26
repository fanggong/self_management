package com.otw.adminapi.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
  Optional<UserEntity> findByPrincipalIgnoreCase(String principal);

  Optional<UserEntity> findByIdAndAccountId(UUID id, UUID accountId);

  Optional<UserEntity> findFirstByAccountIdAndRoleOrderByCreatedAtAscIdAsc(UUID accountId, String role);

  Optional<UserEntity> findFirstByAccountIdOrderByCreatedAtAscIdAsc(UUID accountId);

  boolean existsByPrincipalIgnoreCase(String principal);
}

package com.otw.adminapi.dbt;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DbtRunHistoryRepository extends JpaRepository<DbtRunHistoryEntity, UUID> {
  @Query(
    value = """
      select h
      from DbtRunHistoryEntity h
      where h.accountId = :accountId
      order by
        case when h.startedAt is null then 1 else 0 end,
        h.startedAt desc,
        h.createdAt desc
      """,
    countQuery = """
      select count(h)
      from DbtRunHistoryEntity h
      where h.accountId = :accountId
      """
  )
  Page<DbtRunHistoryEntity> findPageByAccountId(
    @Param("accountId") UUID accountId,
    Pageable pageable
  );

  @Query(
    value = """
      select h
      from DbtRunHistoryEntity h
      where h.accountId = :accountId
        and (
          lower(h.requestedModelName) like concat('%', :search, '%')
          or lower(h.requestedLayer) like concat('%', :search, '%')
          or lower(h.status) like concat('%', :search, '%')
        )
      order by
        case when h.startedAt is null then 1 else 0 end,
        h.startedAt desc,
        h.createdAt desc
      """,
    countQuery = """
      select count(h)
      from DbtRunHistoryEntity h
      where h.accountId = :accountId
        and (
          lower(h.requestedModelName) like concat('%', :search, '%')
          or lower(h.requestedLayer) like concat('%', :search, '%')
          or lower(h.status) like concat('%', :search, '%')
        )
      """
  )
  Page<DbtRunHistoryEntity> findPageByAccountIdAndSearch(
    @Param("accountId") UUID accountId,
    @Param("search") String search,
    Pageable pageable
  );

  Optional<DbtRunHistoryEntity> findByIdAndAccountId(UUID id, UUID accountId);
}

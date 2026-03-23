package com.otw.adminapi.dbt;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DbtRunModelHistoryRepository extends JpaRepository<DbtRunModelHistoryEntity, UUID> {
  @Query("""
    select h.modelName as modelName, h.layer as layer, max(h.completedAt) as completedAt
    from DbtRunModelHistoryEntity h
    where h.accountId = :accountId
      and h.layer = :layer
      and h.status = 'success'
      and h.modelName in :modelNames
    group by h.modelName, h.layer
    """)
  List<DbtModelLatestSuccessfulRunView> findLatestSuccessfulRuns(
    @Param("accountId") UUID accountId,
    @Param("layer") String layer,
    @Param("modelNames") Collection<String> modelNames
  );

  @Query("""
    select h
    from DbtRunModelHistoryEntity h
    where h.dbtRunHistoryId = :runId
      and h.accountId = :accountId
    order by
      case when h.completedAt is null then 1 else 0 end,
      h.completedAt asc,
      h.createdAt asc
    """)
  List<DbtRunModelHistoryEntity> findByRunIdAndAccountIdOrderByExecution(
    @Param("runId") UUID runId,
    @Param("accountId") UUID accountId
  );
}

package com.pokerfriends.server.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TableStateSnapshotRepository extends JpaRepository<TableStateSnapshotEntity, Long> {
  Optional<TableStateSnapshotEntity> findByTableId(String tableId);

  @Modifying
  @Query("delete from TableStateSnapshotEntity t where t.tableId = :tableId")
  int deleteHardByTableId(@Param("tableId") String tableId);
}

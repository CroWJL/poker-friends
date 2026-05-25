package com.pokerfriends.server.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TableStateMetaRepository extends JpaRepository<TableStateMetaEntity, Long> {
  Optional<TableStateMetaEntity> findByTableId(String tableId);
}

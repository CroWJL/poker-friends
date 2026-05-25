package com.pokerfriends.server.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoomRepository extends JpaRepository<RoomEntity, Long> {
  Optional<RoomEntity> findByRoomId(String roomId);
  Optional<RoomEntity> findByTableId(String tableId);

  boolean existsByRoomId(String roomId);

  Page<RoomEntity> findByStatus(RoomStatus status, Pageable pageable);
}

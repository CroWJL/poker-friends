package com.pokerfriends.server.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoomPlayerRepository extends JpaRepository<RoomPlayerEntity, Long> {
  Optional<RoomPlayerEntity> findByRoomIdAndUserId(String roomId, String userId);
}

package com.pokerfriends.server.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
  Optional<UserEntity> findByUserId(String userId);
  Optional<UserEntity> findByDisplayName(String displayName);

  boolean existsByDisplayName(String displayName);
}

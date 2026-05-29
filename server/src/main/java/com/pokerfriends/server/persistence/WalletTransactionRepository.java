package com.pokerfriends.server.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransactionEntity, Long> {
  List<WalletTransactionEntity> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}

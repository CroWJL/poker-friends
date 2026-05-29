package com.pokerfriends.server.persistence;

import com.pokerfriends.server.model.WalletTransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "wallet_transactions")
public class WalletTransactionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 32)
  private String userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private WalletTransactionType type;

  @Column(nullable = false)
  private int amount;

  @Column(nullable = false)
  private int balanceAfter;

  @Column(length = 6)
  private String roomId;

  @Column(length = 32)
  private String tableId;

  @Column(length = 32)
  private String playerId;

  @Column(nullable = false)
  private OffsetDateTime createdAt;

  protected WalletTransactionEntity() {
  }

  public WalletTransactionEntity(
      String userId,
      WalletTransactionType type,
      int amount,
      int balanceAfter,
      String roomId,
      String tableId,
      String playerId
  ) {
    this.userId = userId;
    this.type = type;
    this.amount = amount;
    this.balanceAfter = balanceAfter;
    this.roomId = roomId;
    this.tableId = tableId;
    this.playerId = playerId;
  }

  @PrePersist
  public void onCreate() {
    this.createdAt = OffsetDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public String getUserId() {
    return userId;
  }

  public WalletTransactionType getType() {
    return type;
  }

  public int getAmount() {
    return amount;
  }

  public int getBalanceAfter() {
    return balanceAfter;
  }

  public String getRoomId() {
    return roomId;
  }

  public String getTableId() {
    return tableId;
  }

  public String getPlayerId() {
    return playerId;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}

package com.pokerfriends.server.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
public class UserEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 32)
  private String userId;

  @Column(nullable = false, unique = true, length = 64)
  private String displayName;

  @Column(nullable = false, columnDefinition = "integer not null default 5000")
  private int walletBalance = 5000;

  @Column(nullable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  protected UserEntity() {
  }

  public UserEntity(String userId, String displayName) {
    this.userId = userId;
    this.displayName = displayName;
    this.walletBalance = 5000;
  }

  @PrePersist
  public void onCreate() {
    OffsetDateTime now = OffsetDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  public void onUpdate() {
    this.updatedAt = OffsetDateTime.now();
  }

  public String getUserId() {
    return userId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public int getWalletBalance() {
    return walletBalance;
  }

  public void setWalletBalance(int walletBalance) {
    this.walletBalance = walletBalance;
  }
}

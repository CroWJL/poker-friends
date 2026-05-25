package com.pokerfriends.server.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "rooms")
public class RoomEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 6)
  private String roomId;

  @Column(nullable = false, unique = true)
  private String tableId;

  @Column(nullable = false)
  private int smallBlind;

  @Column(nullable = false)
  private int bigBlind;

  @Column(nullable = false)
  private int maxPlayers;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private RoomStatus status;

  @Column(nullable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  protected RoomEntity() {
  }

  public RoomEntity(
      String roomId,
      String tableId,
      int smallBlind,
      int bigBlind,
      int maxPlayers,
      RoomStatus status
  ) {
    this.roomId = roomId;
    this.tableId = tableId;
    this.smallBlind = smallBlind;
    this.bigBlind = bigBlind;
    this.maxPlayers = maxPlayers;
    this.status = status;
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

  public Long getId() {
    return id;
  }

  public String getRoomId() {
    return roomId;
  }

  public String getTableId() {
    return tableId;
  }

  public int getSmallBlind() {
    return smallBlind;
  }

  public int getBigBlind() {
    return bigBlind;
  }

  public int getMaxPlayers() {
    return maxPlayers;
  }

  public RoomStatus getStatus() {
    return status;
  }

  public void setStatus(RoomStatus status) {
    this.status = status;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}

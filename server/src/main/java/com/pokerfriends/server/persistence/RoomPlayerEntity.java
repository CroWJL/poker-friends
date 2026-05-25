package com.pokerfriends.server.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;

@Entity
@Table(
    name = "room_players",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_room_user", columnNames = {"roomId", "userId"}),
        @UniqueConstraint(name = "uk_room_player", columnNames = {"roomId", "playerId"})
    }
)
public class RoomPlayerEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 6)
  private String roomId;

  @Column(nullable = false, length = 32)
  private String userId;

  @Column(nullable = false, length = 32)
  private String playerId;

  @Column(nullable = false)
  private OffsetDateTime createdAt;

  protected RoomPlayerEntity() {
  }

  public RoomPlayerEntity(String roomId, String userId, String playerId) {
    this.roomId = roomId;
    this.userId = userId;
    this.playerId = playerId;
  }

  @PrePersist
  public void onCreate() {
    this.createdAt = OffsetDateTime.now();
  }

  public String getRoomId() {
    return roomId;
  }

  public String getUserId() {
    return userId;
  }

  public String getPlayerId() {
    return playerId;
  }
}

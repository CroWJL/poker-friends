package com.pokerfriends.server.persistence;

import com.pokerfriends.server.model.PlayerActionType;
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
@Table(name = "action_events")
public class ActionEventEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String tableId;

  @Column(nullable = false)
  private String handId;

  @Column(nullable = false)
  private String playerId;

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  private PlayerActionType actionType;

  @Column
  private Integer amount;

  @Column(nullable = false)
  private boolean accepted;

  @Column(length = 256)
  private String errorMessage;

  @Column(nullable = false, length = 16)
  private String stage;

  @Column(nullable = false)
  private OffsetDateTime createdAt;

  protected ActionEventEntity() {
  }

  public ActionEventEntity(
      String tableId,
      String handId,
      String playerId,
      PlayerActionType actionType,
      Integer amount,
      boolean accepted,
      String errorMessage,
      String stage
  ) {
    this.tableId = tableId;
    this.handId = handId;
    this.playerId = playerId;
    this.actionType = actionType;
    this.amount = amount;
    this.accepted = accepted;
    this.errorMessage = errorMessage;
    this.stage = stage;
  }

  @PrePersist
  public void onCreate() {
    this.createdAt = OffsetDateTime.now();
  }

  public String getTableId() {
    return tableId;
  }

  public String getHandId() {
    return handId;
  }

  public String getPlayerId() {
    return playerId;
  }

  public PlayerActionType getActionType() {
    return actionType;
  }

  public Integer getAmount() {
    return amount;
  }

  public boolean isAccepted() {
    return accepted;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public String getStage() {
    return stage;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}

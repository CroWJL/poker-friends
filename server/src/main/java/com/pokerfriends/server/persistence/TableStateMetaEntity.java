package com.pokerfriends.server.persistence;

import com.pokerfriends.server.model.TableStage;
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
@Table(name = "table_state_meta")
public class TableStateMetaEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String tableId;

  @Column(nullable = false)
  private String handId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private TableStage stage;

  @Column(nullable = false)
  private int pot;

  @Column(nullable = false)
  private int currentBet;

  @Column
  private String actionPlayerId;

  @Column(nullable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  protected TableStateMetaEntity() {
  }

  public TableStateMetaEntity(
      String tableId,
      String handId,
      TableStage stage,
      int pot,
      int currentBet,
      String actionPlayerId
  ) {
    this.tableId = tableId;
    this.handId = handId;
    this.stage = stage;
    this.pot = pot;
    this.currentBet = currentBet;
    this.actionPlayerId = actionPlayerId;
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

  public String getTableId() {
    return tableId;
  }

  public String getHandId() {
    return handId;
  }

  public void setHandId(String handId) {
    this.handId = handId;
  }

  public TableStage getStage() {
    return stage;
  }

  public void setStage(TableStage stage) {
    this.stage = stage;
  }

  public int getPot() {
    return pot;
  }

  public void setPot(int pot) {
    this.pot = pot;
  }

  public int getCurrentBet() {
    return currentBet;
  }

  public void setCurrentBet(int currentBet) {
    this.currentBet = currentBet;
  }

  public String getActionPlayerId() {
    return actionPlayerId;
  }

  public void setActionPlayerId(String actionPlayerId) {
    this.actionPlayerId = actionPlayerId;
  }
}

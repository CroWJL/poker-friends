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
@Table(name = "table_state_snapshot")
public class TableStateSnapshotEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String tableId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String snapshotJson;

  @Column(nullable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  protected TableStateSnapshotEntity() {
  }

  public TableStateSnapshotEntity(String tableId, String snapshotJson) {
    this.tableId = tableId;
    this.snapshotJson = snapshotJson;
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

  public String getSnapshotJson() {
    return snapshotJson;
  }

  public void setSnapshotJson(String snapshotJson) {
    this.snapshotJson = snapshotJson;
  }
}

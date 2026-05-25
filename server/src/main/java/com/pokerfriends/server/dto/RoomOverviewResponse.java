package com.pokerfriends.server.dto;

import com.pokerfriends.server.model.TableStage;

import java.time.OffsetDateTime;

public record RoomOverviewResponse(
    String roomId,
    String tableId,
    int smallBlind,
    int bigBlind,
    int maxPlayers,
    String status,
    OffsetDateTime roomCreatedAt,
    OffsetDateTime roomUpdatedAt,
    TableMeta tableMeta
) {
  public record TableMeta(
      String handId,
      TableStage stage,
      int pot,
      int currentBet,
      String actionPlayerId
  ) {
  }
}

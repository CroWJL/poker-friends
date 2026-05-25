package com.pokerfriends.server.dto;

public record RoomResponse(
    String roomId,
    String tableId,
    String playerId,
    String token
) {
}

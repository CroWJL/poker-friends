package com.pokerfriends.server.dto;

import java.time.OffsetDateTime;

public record ActionEventResponse(
    String tableId,
    String handId,
    String playerId,
    String actionType,
    Integer amount,
    boolean accepted,
    String errorMessage,
    String stage,
    OffsetDateTime createdAt
) {
}

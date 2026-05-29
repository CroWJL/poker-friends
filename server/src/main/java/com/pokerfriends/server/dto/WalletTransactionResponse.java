package com.pokerfriends.server.dto;

import com.pokerfriends.server.model.WalletTransactionType;

import java.time.OffsetDateTime;

public record WalletTransactionResponse(
    long id,
    WalletTransactionType type,
    int amount,
    int balanceAfter,
    String roomId,
    String tableId,
    String playerId,
    OffsetDateTime createdAt
) {
}

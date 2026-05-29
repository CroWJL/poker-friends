package com.pokerfriends.server.dto;

public record WalletResponse(
    String displayName,
    int walletBalance
) {
}

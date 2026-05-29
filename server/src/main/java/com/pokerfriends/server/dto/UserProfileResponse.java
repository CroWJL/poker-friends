package com.pokerfriends.server.dto;

public record UserProfileResponse(
    String userId,
    String displayName,
    int walletBalance
) {
}

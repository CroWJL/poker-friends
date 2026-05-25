package com.pokerfriends.server.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateRoomRequest(
    @NotBlank String hostName,
    @Min(1) int smallBlind,
    @Min(1) int bigBlind,
    @Min(2) @Max(9) int maxPlayers
) {
}

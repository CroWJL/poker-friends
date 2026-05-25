package com.pokerfriends.server.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinRoomRequest(@NotBlank String playerName) {
}

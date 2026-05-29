package com.pokerfriends.server.dto;

import jakarta.validation.constraints.NotBlank;

public record LeaveRoomRequest(@NotBlank String playerName) {
}

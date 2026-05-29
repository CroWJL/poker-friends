package com.pokerfriends.server.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePracticeRoomRequest(@NotBlank String hostName) {
}

package com.pokerfriends.server.dto;

import com.pokerfriends.server.model.PlayerActionType;

public record ActionCommand(PlayerActionType type, Integer amount) {
}

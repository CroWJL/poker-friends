package com.pokerfriends.server.dto;

public record WsEnvelope(String event, Object payload) {
}
